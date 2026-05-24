package com.lukksarna.skystarter.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class HexagonalArchitectureTest {

    private static final String DOMAIN_PACKAGE = "com.lukksarna.skystarter.domain..";
    private static final String SERVICE_PACKAGE = "com.lukksarna.skystarter.service..";
    private static final String INFRASTRUCTURE_PACKAGE = "com.lukksarna.skystarter.infrastructure..";
    private static final String APP_PACKAGE = "com.lukksarna.skystarter.app..";
    private static final String REST_PACKAGE = "com.lukksarna.skystarter.infrastructure.api.rest..";

    private static final JavaClasses CLASSES = importProjectClasses();

    private static JavaClasses importProjectClasses() {
        String root = resolveProjectRoot();
        java.nio.file.Path[] paths = {
                java.nio.file.Paths.get(root, "domain", "build", "classes", "java", "main"),
                java.nio.file.Paths.get(root, "service", "build", "classes", "java", "main"),
                java.nio.file.Paths.get(root, "infrastructure", "build", "classes", "java", "main"),
                java.nio.file.Paths.get(root, "app", "build", "classes", "java", "main")
        };
        for (java.nio.file.Path path : paths) {
            if (!java.nio.file.Files.isDirectory(path)) {
                throw new IllegalStateException("expected class directory missing: " + path);
            }
        }
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPaths(paths);
    }

    // Gradle sets project.root.dir on the test task. When the test is run from
    // an IDE that property is absent, so fall back to walking up from the
    // working directory to the Gradle root (the directory with settings.gradle.kts).
    private static String resolveProjectRoot() {
        String root = System.getProperty("project.root.dir");
        if (root != null) {
            return root;
        }
        java.nio.file.Path dir = java.nio.file.Paths.get("").toAbsolutePath();
        while (dir != null) {
            if (java.nio.file.Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir.toString();
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "Could not locate the project root: project.root.dir is unset and no settings.gradle.kts "
                        + "was found walking up from " + java.nio.file.Paths.get("").toAbsolutePath());
    }

    @Test
    void domain_has_no_spring_stereotypes() {
        noClasses().that().resideInAPackage(DOMAIN_PACKAGE)
                .should().beAnnotatedWith("org.springframework.stereotype.Service")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Repository")
                .orShould().beAnnotatedWith("org.springframework.context.annotation.Configuration")
                .because("domain is framework-agnostic; @Component is permitted only because Axon 5's"
                        + " Entity Model requires command-handler classes (e.g. SkyCommandHandlers) to be a"
                        + " Spring bean for the Axon-Spring bridge -- per AGENTS.md")
                .check(CLASSES);
    }

    @Test
    void domain_does_not_depend_on_jpa() {
        noClasses().that().resideInAPackage(DOMAIN_PACKAGE)
                .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "javax.persistence..")
                .because("JPA entities live in infrastructure.persistence.entity, not domain")
                .check(CLASSES);
    }

    @Test
    void no_legacy_java_util_date() {
        noClasses().should().dependOnClassesThat()
                .haveFullyQualifiedName("java.util.Date")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.sql.Date")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.sql.Timestamp")
                .because("use java.time.* instead")
                .check(CLASSES);
    }

    @Test
    void rest_controllers_live_in_infrastructure_api_rest() {
        classes().that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should().resideInAPackage(REST_PACKAGE)
                .because("REST adapters belong in the infrastructure inbound API package")
                .check(CLASSES);
    }

    @Test
    void dependencies_only_point_inward() {
        layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer("Domain").definedBy(DOMAIN_PACKAGE)
                .layer("Service").definedBy(SERVICE_PACKAGE)
                .layer("Infrastructure").definedBy(INFRASTRUCTURE_PACKAGE)
                .layer("App").definedBy(APP_PACKAGE)
                .whereLayer("App").mayNotBeAccessedByAnyLayer()
                .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("App")
                .whereLayer("Service").mayOnlyBeAccessedByLayers("Infrastructure", "App")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Service", "Infrastructure", "App")
                .because("dependencies must point inward: app -> infrastructure -> service -> domain")
                .check(CLASSES);
    }
}
