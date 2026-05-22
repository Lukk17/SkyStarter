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

    runtimeOnly(libs.micrometer.registry.prometheus)
    runtimeOnly(libs.micrometer.tracing.bridge.otel)
    runtimeOnly(libs.opentelemetry.exporter.otlp)
    runtimeOnly(libs.logstash.logback.encoder)

    developmentOnly(libs.spring.boot.devtools)

    testImplementation(libs.bundles.spring.testing)
    testImplementation(libs.spring.boot.starter.web)
    testImplementation(libs.spring.boot.starter.oauth2.resourceserver)
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
    apiDocsUrl.set("http://localhost:$serverPort$apiDocsPaths.yaml")
    outputDir.set(file("${rootDir}/docs/api"))
    outputFileName.set("openapi.yaml")
    waitTimeInSeconds.set(15)
    customBootRun {
        args.set(listOf("--spring.profiles.active=local"))
    }
}

// Refresh docs/api/openapi.yaml on every ./gradlew build. The plugin's task
// forks a Spring Boot process, hits /openapi/v3/api-docs.yaml, writes the
// spec to outputDir, and shuts the process down. Requires PostgreSQL,
// MongoDB, and (optionally) Keycloak reachable per docs/running.md.
tasks.named("build") {
    dependsOn("generateOpenApiDocs")
}

// The springdoc-openapi-gradle-plugin and the underlying JavaExecFork plugin
// both store Task references and BootRun internals that Gradle 9's
// configuration cache can't serialize. Mark the tasks incompatible so the
// rest of the build cache keeps working.
tasks.named("generateOpenApiDocs") {
    notCompatibleWithConfigurationCache("springdoc-openapi plugin captures Task references")
}
tasks.named("forkedSpringBootRun") {
    notCompatibleWithConfigurationCache("JavaExecFork plugin captures Task references")
    // The plugin invokes the forked JVM with subproject jars on its
    // classpath but doesn't declare them as inputs, so Gradle 9 warns
    // about implicit dependencies. Wire the jars explicitly.
    dependsOn(":domain:jar", ":service:jar", ":infrastructure:jar")
}
tasks.named("forkedSpringBootStop") {
    notCompatibleWithConfigurationCache("JavaExecFork plugin captures Task references")
}
