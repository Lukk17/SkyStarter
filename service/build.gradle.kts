dependencies {
    implementation(project(":domain"))

    implementation(libs.axon.spring.boot.starter)

    testImplementation(libs.axon.test)
}
