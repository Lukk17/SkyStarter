val projectEncoding: String = "UTF-8"
val projectJavaVersion: Int = 25
val projectJavaVendor: JvmVendorSpec = JvmVendorSpec.ADOPTIUM

disableSpotlessAutoTasks()

plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spotless) apply false // build-logic setup it
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.owasp.dependency.check)
    alias(libs.plugins.dependency.analysis)
}

allprojects {
    repositories {
        mavenCentral()
    }

    tasks.withType<JavaCompile> {
        options.encoding = projectEncoding
    }

    tasks.withType<Test> {
        systemProperty("file.encoding", projectEncoding)
    }

    plugins.withType<JavaPlugin> {
        configure<JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(projectJavaVersion)
                vendor.set(projectJavaVendor)
            }
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "java-library")
    apply(plugin = "jacoco")

    group = "com.revdevs.pharmacy"
    version = "0.0.1-SNAPSHOT"
    description = "SupplierConnect"

    val libs = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")

    // BOMs are imported as Gradle `platform(...)` constraints below — no need
    // for the legacy io.spring.dependency-management plugin. Spring Boot's
    // Gradle plugin works with native platform imports since Boot 3.x.

    // Configurations the Spring Boot Gradle plugin creates lazily (developmentOnly,
    // testAndDevelopmentOnly) need BOM resolution too. Apply platform imports
    // wherever the plugin happens to register them.
    val bomConfigurations = listOf(
        "implementation",
        "annotationProcessor",
        "compileOnly",
        "runtimeOnly",
        "testImplementation",
        "testCompileOnly",
        "testRuntimeOnly",
        "developmentOnly",
        "testAndDevelopmentOnly"
    )

    afterEvaluate {
        bomConfigurations.forEach { configName ->
            configurations.findByName(configName)?.let { _ ->
                dependencies.add(configName, dependencies.platform(libs.findLibrary("spring-boot-bom").get()))
                dependencies.add(configName, dependencies.platform(libs.findLibrary("spring-cloud-bom").get()))
                dependencies.add(configName, dependencies.platform(libs.findLibrary("axon-bom").get()))
                if (configName.startsWith("test")) {
                    dependencies.add(configName, dependencies.platform(libs.findLibrary("testcontainers-bom").get()))
                }
            }
        }
    }

    dependencies {

        "compileOnly"(libs.findLibrary("lombok").get())
        "annotationProcessor"(libs.findLibrary("lombok").get())

        "compileOnly"(libs.findLibrary("slf4j").get())

        "testImplementation"(libs.findBundle("testing-base").get())
        "testImplementation"(libs.findLibrary("archunit-junit5").get())
    }
}

dependencyCheck {
    formats = listOf("HTML", "JSON")
    failBuildOnCVSS = 7.0f
    analyzers.assemblyEnabled = false // Skip .NET analysis (not needed for Java)
}

fun disableSpotlessAutoTasks() {
    gradle.taskGraph.whenReady {
        allTasks
            .filter { it.hasProperty("spotlessApply") || it.hasProperty("spotlessCheck") }
            .forEach { it.enabled = false }
    }
}

// ----------------------------------------------------------------------------
// Migration-coverage guard
// ----------------------------------------------------------------------------
// Fails the build if @Entity-bearing class files have changed since the last
// recorded SHA without a corresponding new file under
// infrastructure/src/main/resources/db/changelog/. Override per-commit with
// `[no-migration]` in the commit message (use sparingly: only for changes
// that don't affect persistence — e.g. method-only refactors).
//
// See ADR-0009 and openspec/specs/database-migrations/spec.md.
val verifyMigrationCoverage = tasks.register("verifyMigrationCoverage") {
    group = "verification"
    description = "Fails if JPA entities changed without an accompanying Liquibase changeset."

    val classesRoot = rootProject.file("infrastructure/build/classes/java/main")
    val changelogRoot = rootProject.file("infrastructure/src/main/resources/db/changelog")
    val shaFile = rootProject.layout.buildDirectory.file("migration-coverage.sha").get().asFile

    inputs.dir(classesRoot).withPropertyName("entityClasses").optional(true)
    inputs.dir(changelogRoot).withPropertyName("changelog").optional(true)
    outputs.file(shaFile)

    doLast {
        if (!classesRoot.exists()) {
            logger.lifecycle("verifyMigrationCoverage: no compiled classes yet, skipping.")
            return@doLast
        }
        // Hash all class files whose bytecode references jakarta.persistence.Entity.
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val entityMarker = "Ljakarta/persistence/Entity;".toByteArray(Charsets.UTF_8)
        val entityFiles = classesRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".class") }
            .filter { it.readBytes().let { b -> indexOf(b, entityMarker) >= 0 } }
            .sortedBy { it.relativeTo(classesRoot).path.replace(java.io.File.separatorChar, '/') }
            .toList()

        if (entityFiles.isEmpty()) {
            logger.lifecycle("verifyMigrationCoverage: no @Entity classes found, skipping.")
            shaFile.parentFile.mkdirs()
            shaFile.writeText("")
            return@doLast
        }

        entityFiles.forEach { f ->
            md.update(f.relativeTo(classesRoot).path.toByteArray(Charsets.UTF_8))
            md.update(f.readBytes())
        }
        val newSha = md.digest().joinToString("") { "%02x".format(it) }

        val previousSha = if (shaFile.exists()) shaFile.readText().trim() else ""
        if (previousSha == newSha) {
            logger.lifecycle("verifyMigrationCoverage: entity bytecode unchanged ✔")
            return@doLast
        }

        // Bytecode changed. Was a new changelog file added since the last commit?
        val gitDiff = providers.exec {
            commandLine("git", "diff", "--name-only", "HEAD~1", "HEAD",
                "--", "infrastructure/src/main/resources/db/changelog/")
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim()
        val changelogTouched = gitDiff.lines().any { it.isNotBlank() }

        // And was the override marker present in the most recent commit?
        val lastMessage = providers.exec {
            commandLine("git", "log", "-1", "--pretty=%B")
            isIgnoreExitValue = true
        }.standardOutput.asText.get()
        val overrideMarkerPresent = lastMessage.contains("[no-migration]")

        if (!changelogTouched && !overrideMarkerPresent) {
            val nextNumber = (changelogRoot.listFiles { _, name -> name.matches(Regex("\\d{4}-.*\\.yaml")) }
                ?.maxOfOrNull { it.name.substringBefore('-').toInt() } ?: 0) + 1
            val nextName = "%04d-<short-slug>.yaml".format(nextNumber)
            val changedEntities = entityFiles.joinToString("\n  - ") {
                it.relativeTo(classesRoot).path.replace(java.io.File.separatorChar, '/')
            }
            throw GradleException(
                """
                verifyMigrationCoverage failed.

                JPA @Entity bytecode changed since the previous run, but no new
                changelog file was added under
                infrastructure/src/main/resources/db/changelog/.

                Changed @Entity classes:
                  - $changedEntities

                Add a new changeset:
                  infrastructure/src/main/resources/db/changelog/$nextName

                If this entity change does not affect persisted state (e.g. a
                method-only refactor, a @Transient field, or a non-persistent
                annotation tweak), include `[no-migration]` in the commit
                message.
                """.trimIndent()
            )
        }

        shaFile.parentFile.mkdirs()
        shaFile.writeText(newSha)
        logger.lifecycle("verifyMigrationCoverage: entity bytecode changed, but migration accompaniment satisfied ✔")
    }
}

// Make `check` depend on the guard, but only after infrastructure compiles.
gradle.projectsEvaluated {
    rootProject.subprojects.find { it.name == "infrastructure" }?.let { infra ->
        verifyMigrationCoverage.configure { dependsOn(infra.tasks.named("compileJava")) }
    }
    tasks.findByName("check")?.dependsOn(verifyMigrationCoverage)
}

// Naive byte-array index-of for the @Entity marker.
fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
    if (needle.isEmpty()) return 0
    outer@ for (i in 0..haystack.size - needle.size) {
        for (j in needle.indices) {
            if (haystack[i + j] != needle[j]) continue@outer
        }
        return i
    }
    return -1
}
