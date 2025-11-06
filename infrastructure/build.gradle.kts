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
    implementation(libs.gson)

    implementation(libs.axon.spring.boot.starter)
    runtimeOnly(libs.postgres)
    implementation(libs.liquibase.core)
    implementation(libs.liquibase.mongodb)

    testImplementation(libs.bundles.spring.testing)
    testImplementation(libs.axon.test)
}

