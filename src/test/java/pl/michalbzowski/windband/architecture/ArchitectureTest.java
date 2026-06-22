package pl.michalbzowski.windband.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("pl.michalbzowski.windband");
    }

    @Test
    void domainShouldNotDependOnApplication() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("..application..")
                .check(classes);
    }

    @Test
    void domainShouldNotDependOnAdapters() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("..adapter..")
                .check(classes);
    }

    @Test
    void domainShouldNotDependOnSpringFramework() {
        // Note: domain entities use JPA annotations (@Entity, @Table) for practical reasons.
        // Repository interfaces must extend Spring Data JpaRepository — this is a known compromise.
        // We check that no OTHER domain classes (non-Repository) depend on Spring.
        noClasses()
                .that().resideInAPackage("..domain..")
                .and().areNotInterfaces()
                .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                .check(classes);
    }

    @Test
    void applicationShouldNotDependOnAdapters() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..adapter..")
                .check(classes);
    }

    @Test
    void applicationShouldNotDependOnSpringWeb() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework.web..")
                .check(classes);
    }

    @Test
    void domainRepositoriesShouldBeInterfaces() {
        classes()
                .that().resideInAPackage("..domain..")
                .and().haveSimpleNameEndingWith("Repository")
                .should().beInterfaces()
                .check(classes);
    }

    @Test
    void commandServicesShouldResideInCommandPackage() {
        classes()
                .that().haveSimpleNameEndingWith("CommandService")
                .should().resideInAPackage("..command..")
                .check(classes);
    }

    @Test
    void queryServicesShouldResideInQueryPackage() {
        classes()
                .that().haveSimpleNameEndingWith("QueryService")
                .should().resideInAPackage("..query..")
                .check(classes);
    }

    @Test
    void controllersShouldNotUseDomainRepositoriesDirectly() {
        noClasses()
                .that().resideInAPackage("..adapter.in.web..")
                .and().haveSimpleNameNotEndingWith("AdminController")
                .should().dependOnClassesThat()
                .haveSimpleNameEndingWith("Repository")
                .check(classes);
    }
}
