package com.lukksarna.skystarter.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class HexagonalArchitectureTest {

    private static final String DOMAIN_PACKAGE = "com.lukksarna.skystarter.domain..";
    private static final String REST_PACKAGE = "com.lukksarna.skystarter.infrastructure.api.rest..";

    private static final JavaClasses CLASSES = importProjectClasses();

    private static JavaClasses importProjectClasses() {
        String root = System.getProperty("project.root.dir");
        if (root == null) {
            throw new IllegalStateException("project.root.dir system property not set; see build.gradle.kts test task");
        }
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
}
