package ke.sauti.hms.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** The application's own classes (tests excluded) must follow the layering rules. */
class ArchitectureTest {

    private static final JavaClasses APPLICATION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ArchitectureRules.ROOT);

    @Test
    void controllersDoNotUseRepositories() {
        ArchitectureRules.CONTROLLERS_DO_NOT_USE_REPOSITORIES.check(APPLICATION_CLASSES);
    }

    @Test
    void featuresUseOnlyTheirOwnRepositories() {
        ArchitectureRules.FEATURES_USE_ONLY_THEIR_OWN_REPOSITORIES.check(APPLICATION_CLASSES);
    }
}
