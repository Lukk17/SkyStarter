import org.springframework.boot.gradle.tasks.bundling.BootJar
import java.nio.file.Files
import java.nio.file.Paths

plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.springdoc.openapi)
}

dependencies {
    implementation(project(":infrastructure"))
    implementation(libs.spring.boot.starter)
    implementation(libs.postgres)
    implementation(libs.axon.spring.boot.starter)

    developmentOnly(libs.spring.boot.devtools)

    testImplementation(libs.bundles.spring.testing)
}

tasks.named<BootJar>("bootJar") {
    archiveFileName.set("${rootProject.name}.jar")
}

// Disable the plain jar to avoid the *.jar conflict
tasks.named("jar") {
    enabled = false
}

fun getPropertyFromYaml(propertyPattern: String): String {
    try {
        val appYamlPath = Paths.get("${rootDir}/app/src/main/resources/application.yaml")

        if (Files.exists(appYamlPath)) {
            val content = Files.readString(appYamlPath)
            val regex = Regex(propertyPattern).find(content)

            return regex?.groupValues?.get(1)?.trim()
                ?: throw IllegalArgumentException("Property $propertyPattern not found in application.yaml")
        }
    } catch (e: Exception) {
        println("Error while reading property from application.yaml: ${e.message}")
    }
    throw IllegalArgumentException("application.yaml not found")
}

val serverPort = getPropertyFromYaml("server:\\s*port:\\s*(\\d+)")
val apiDocsPaths = getPropertyFromYaml("api-docs:\\s*path:\\s*\"([^\"]+)\"")

openApi {
    apiDocsUrl.set("http://localhost:$serverPort/supplier$apiDocsPaths.yaml")
    outputDir.set(file("${rootDir}/docs/api"))
    outputFileName.set("openapi.yaml")
    waitTimeInSeconds.set(15)
    customBootRun {
        args.set(listOf("--spring.profiles.active=local"))
    }
}
