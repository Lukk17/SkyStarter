val projectEncoding: String = "UTF-8"
val projectJavaVersion: Int = 25
val projectJavaVendor: JvmVendorSpec = JvmVendorSpec.ADOPTIUM

plugins {
    base
    alias(libs.plugins.spring.boot) apply false
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
    description = "SkyStarter"

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

