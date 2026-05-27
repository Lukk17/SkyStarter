dependencies {
    implementation(libs.axon.spring.boot.starter)
    implementation(libs.axon.modelling)
    implementation(libs.axon.eventsourcing)

    // Axon 5's SpringEventSourcedEntityLookup discovers entities via
    // getBeanNamesForAnnotation(EventSourcedEntity.class) on the Spring bean
    // factory; the aggregate must therefore be a Spring bean. The light
    // compileOnly dep gives us @Component / @Scope without dragging the
    // wider Spring runtime into the domain. See ADR-0010.
    compileOnly("org.springframework:spring-context")

    testImplementation(libs.axon.test)
}
