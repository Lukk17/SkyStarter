val projectEncoding: String = "UTF-8"
val projectJavaVersion: Int = 25
val projectJavaVendor: JvmVendorSpec = JvmVendorSpec.ADOPTIUM

disableSpotlessAutoTasks()

plugins {
    base
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

    // JaCoCo coverage baseline: 0.80 INSTRUCTION. Excludes Spring bean-wiring
    // and Hibernate plumbing — those aren't meaningful unit-test targets and
    // are covered by Spring context bootstrap or integration tests already.
    val coverageExcludes = listOf(
        "**/AppApplication.class",
        "**/*Config.class",
        "**/*Configuration.class",
        "**/SkyUser.class",
        "**/ByteaEnforcedPostgresSQLDialect.class",
        "**/*MapperImpl.class",
        "**/ApiCommonErrorResponses.class",
        "**/ApiCommonSuccessResponses.class",
        "**/ProblemTypes.class"
    )

    tasks.withType<JacocoReport>().configureEach {
        classDirectories.setFrom(
            files(classDirectories.files.map { fileTree(it) { exclude(coverageExcludes) } })
        )
    }

    tasks.withType<JacocoCoverageVerification>().configureEach {
        classDirectories.setFrom(
            files(classDirectories.files.map { fileTree(it) { exclude(coverageExcludes) } })
        )
        violationRules {
            rule {
                limit {
                    counter = "INSTRUCTION"
                    minimum = "0.80".toBigDecimal()
                }
            }
        }
    }

    // Unit tests run in parallel JVM forks; integration tests (*IT) run
    // sequentially in their own task because Testcontainers + @SpringBootTest
    // contexts don't tolerate cross-fork data races on shared containers.
    // Coverage report and gate consume the combined exec data from both.
    val sourceSets = extensions.getByType(SourceSetContainer::class.java)
    val integrationTest = tasks.register<Test>("integrationTest") {
        description = "Runs integration tests sequentially (classes under ..integration..)."
        group = "verification"
        useJUnitPlatform()
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        include("**/integration/**/*.class")
        shouldRunAfter("test")
        maxParallelForks = 1
        // Fresh JVM per IT class. @SpringBootTest contexts share an Axon
        // lifecycle bean ("axon-start-lifecycle-handler-N") that occasionally
        // fails to restart cleanly when a second context boots in the same
        // JVM. Isolation per class costs ~5-10s of Spring boot but keeps
        // Testcontainers reuse intact (containers persist across forks).
        forkEvery = 1
        // Project-default Testcontainers reuse: the env var is one of the two
        // sources Testcontainers checks for the reuse flag (the other is
        // ~/.testcontainers.properties). System properties are NOT read for
        // this flag -- documented at https://java.testcontainers.org/features/reuse
        environment("TESTCONTAINERS_REUSE_ENABLE", "true")
    }

    tasks.named<Test>("test") {
        exclude("**/integration/**/*.class")
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
        systemProperty("project.root.dir", rootProject.projectDir.absolutePath)
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn("test", integrationTest)
        executionData(
            fileTree(layout.buildDirectory)
                .include("jacoco/test.exec", "jacoco/integrationTest.exec")
        )
    }

    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn("test", integrationTest)
        executionData(
            fileTree(layout.buildDirectory)
                .include("jacoco/test.exec", "jacoco/integrationTest.exec")
        )
    }

    tasks.named("check") {
        dependsOn(integrationTest)
        dependsOn("jacocoTestCoverageVerification")
    }
}

dependencyCheck {
    formats = listOf("HTML", "JSON")
    failBuildOnCVSS = 7.0f
    analyzers.assemblyEnabled = false // Skip .NET analysis (not needed for Java)
    suppressionFile = "${rootDir}/dependency-check-suppressions.xml"
}

// OWASP and springdoc plugins both store Task references at execution time,
// which Gradle 9's configuration cache rejects. The tasks still run, but
// the cache entry is discarded and the next run is slow. Marking them
// incompatible lets the cache work for everything else.
tasks.withType<org.owasp.dependencycheck.gradle.tasks.Analyze>().configureEach {
    notCompatibleWithConfigurationCache("OWASP dependency-check plugin reads Project at execution time")
}
tasks.withType<org.owasp.dependencycheck.gradle.tasks.Aggregate>().configureEach {
    notCompatibleWithConfigurationCache("OWASP dependency-check plugin reads Project at execution time")
}

// dependencyCheckAggregate is NOT wired into check because OWASP's plugin
// resolves subproject configurations from the aggregate task at execution
// time, which Gradle 9 rejects under org.gradle.parallel=true ("Resolution
// of the configuration ':app:annotationProcessor' was attempted without an
// exclusive lock"). Two options would be to disable parallel project
// execution globally (slows every build) or to gate dep-check separately.
// We pick the latter: run the aggregate manually or in CI with the
// no-parallel flag. See docs/development.md for the exact command.

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
    notCompatibleWithConfigurationCache("custom task references outer build-script helper functions")

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
