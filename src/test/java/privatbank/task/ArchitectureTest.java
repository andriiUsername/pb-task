package privatbank.task;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("privatbank.task");

    @Test
    void layersDependOnlyOnLowerLayers() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Controller").definedBy("..controller..")
                .layer("Scheduler").definedBy("..scheduler..")
                .layer("Service").definedBy("..service..")
                .layer("Repository").definedBy("..repository..")
                .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
                .whereLayer("Scheduler").mayNotBeAccessedByAnyLayer()
                .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Scheduler")
                .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
                .check(CLASSES);
    }

    @Test
    void controllersDoNotExposeEntities() {
        noClasses().that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
                .check(CLASSES);
    }

    @Test
    void packagesHaveNoDependencyCycles() {
        slices().matching("privatbank.task.(*)..").should().beFreeOfCycles().check(CLASSES);
    }
}
