// Liquibase Gradle plugin 3.x requires Liquibase to be visible at plugin-apply
// time, so we declare it on the `buildscript` classpath in addition to the
// `liquibaseRuntime` configuration the plugin uses at task-execution time.
// See https://github.com/liquibase/liquibase-gradle-plugin/blob/main/doc/usage.md
buildscript {
    val liquibaseCoreVersion: String =
        rootProject.extensions.getByType<VersionCatalogsExtension>()
            .named("libs")
            .findVersion("liquibaseCore")
            .get()
            .requiredVersion

    repositories { mavenCentral() }
    dependencies {
        classpath("org.liquibase:liquibase-core:$liquibaseCoreVersion")
    }
}

plugins {
    alias(libs.plugins.liquibase.gradle)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":service"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.oauth2.resourceserver)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.data.mongodb)
    implementation(libs.springdoc.openapi.starter)

    implementation(libs.spring.boot.devtools)

    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)
    annotationProcessor(libs.lombok.mapstruct.binding)

    implementation(libs.axon.spring.boot.starter)
    runtimeOnly(libs.postgres)
    runtimeOnly(libs.spring.boot.starter.liquibase)

    // Author-time only: lets `./gradlew :infrastructure:liquibaseUpdate` etc. run.
    // `liquibaseRuntime` is plugin-created and outside the root subprojects'
    // BOM-managed configurations, so the Spring Boot BOM is imported here too.
    "liquibaseRuntime"(platform(libs.spring.boot.bom))
    "liquibaseRuntime"(libs.liquibase.core)
    "liquibaseRuntime"(libs.postgres)
    "liquibaseRuntime"("info.picocli:picocli:4.7.6")

    testImplementation(libs.bundles.spring.testing)
    testImplementation(libs.axon.test)
}

// Liquibase Gradle plugin — author-time tasks only. Runtime migrations are driven by
// Spring Boot's LiquibaseAutoConfiguration when the app starts.
//
// Pass connection details on the command line, e.g.:
//   ./gradlew :infrastructure:liquibaseStatus \
//     -Pliquibase.url=jdbc:postgresql://localhost:5432/starter \
//     -Pliquibase.username=postgres \
//     -Pliquibase.password=local
liquibase {
    activities.register("main") {
        this.arguments = mapOf(
            "logLevel" to "info",
            "changelogFile" to "src/main/resources/db/changelog/db.changelog-master.yaml",
            "url" to (project.findProperty("liquibase.url") as String? ?: ""),
            "username" to (project.findProperty("liquibase.username") as String? ?: ""),
            "password" to (project.findProperty("liquibase.password") as String? ?: ""),
            "driver" to "org.postgresql.Driver",
        )
    }
    runList = "main"
}
