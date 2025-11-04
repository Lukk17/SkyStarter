import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension

val projectEncoding: String = "UTF-8"
val projectJavaVersion: Int = 21
val projectJavaVendor: JvmVendorSpec = JvmVendorSpec.ADOPTIUM

disableSpotlessAutoTasks()

plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false // build-logic setup it
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
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "jacoco")

    group = "com.revdevs.pharmacy"
    version = "0.0.1-SNAPSHOT"
    description = "SupplierConnect"

    val libs = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")

    the<DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.findVersion("springBoot").get()}")
        }
    }

    dependencies {
        "compileOnly"(libs.findLibrary("lombok").get())
        "annotationProcessor"(libs.findLibrary("lombok").get())

        "compileOnly"(libs.findLibrary("slf4j").get())

        "testImplementation"(libs.findBundle("testing-base").get())
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
