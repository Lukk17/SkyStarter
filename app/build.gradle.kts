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
    waitTimeInSeconds.set(120)
    customBootRun {
        // The `openapi` profile boots the app with all heavy autoconfigs
        // (JPA / MongoDB / Liquibase / OAuth2 resource server) excluded and
        // lazy-initialization on. OpenApiStubConfig provides @Primary stub
        // SkyCommandService / SkyQueryService beans plus a permissive
        // SecurityFilterChain so springdoc can scrape the spec without
        // PostgreSQL, MongoDB, or Keycloak running. See
        // application-openapi.yaml and OpenApiStubConfig for the full
        // contract.
        args.set(listOf("--spring.profiles.active=openapi"))
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
val javaToolchains = extensions.getByType<JavaToolchainService>()
val javaPluginExt = extensions.getByType<JavaPluginExtension>()

tasks.named<com.github.psxpaul.task.JavaExecFork>("forkedSpringBootRun") {
    notCompatibleWithConfigurationCache("JavaExecFork plugin captures Task references")
    standardOutput.set(layout.buildDirectory.file("tmp/forkedSpringBootRun/stdout.log"))
    errorOutput.set(layout.buildDirectory.file("tmp/forkedSpringBootRun/stderr.log"))
    // Block until the app is listening on the configured port before
    // returning -- otherwise generateOpenApiDocs starts polling a port
    // that isn't open yet and times out before the app finishes booting.
    waitForPort = serverPort.toInt()
    timeout = 300
    // Upstream plugin bug #1: forkedSpringBootRun uses BootRun's classpath
    // (which references subproject jars) but doesn't declare those jar
    // tasks as inputs. Gradle 9 warns about implicit dependencies. Wire
    // explicit dependsOn until the plugin is fixed.
    dependsOn(":domain:jar", ":service:jar", ":infrastructure:jar")
    // Upstream plugin bug #2: the plugin launches the forked JVM using the
    // Gradle daemon's JVM (Java 21 on Gradle 9.x) instead of the project's
    // Java toolchain. With Java 25 bytecode in the jars that fails with
    // UnsupportedClassVersionError (class version 69 vs runtime 65). Pin
    // the launcher to the project's toolchain explicitly. JavaExecFork
    // exposes javaLauncher directly (it's not a JavaExec subclass, even
    // though it looks like one in the Gradle UI).
    javaLauncher.set(javaToolchains.launcherFor(javaPluginExt.toolchain))
}
tasks.named("forkedSpringBootStop") {
    notCompatibleWithConfigurationCache("JavaExecFork plugin captures Task references")
}
