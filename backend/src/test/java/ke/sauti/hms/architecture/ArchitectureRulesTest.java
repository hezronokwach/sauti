package ke.sauti.hms.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import ke.sauti.hms.archfixture.CrossFeatureService;
import ke.sauti.hms.archfixture.LeakyController;
import ke.sauti.hms.archfixture.OwnRepositoryService;
import org.junit.jupiter.api.Test;

/** The rules really fail on code that breaks them, and only on that code. */
class ArchitectureRulesTest {

    private static final JavaClasses FIXTURES = new ClassFileImporter().importPackages("ke.sauti.hms.archfixture");

    @Test
    void controllerUsingARepositoryIsRejected() {
        assertThatThrownBy(() -> ArchitectureRules.CONTROLLERS_DO_NOT_USE_REPOSITORIES.check(FIXTURES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(LeakyController.class.getName())
                .hasMessageNotContaining(OwnRepositoryService.class.getName());
    }

    @Test
    void usingAnotherFeaturesRepositoryIsRejected() {
        assertThatThrownBy(() -> ArchitectureRules.FEATURES_USE_ONLY_THEIR_OWN_REPOSITORIES.check(FIXTURES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(CrossFeatureService.class.getName())
                .hasMessageContaining("FacilitySettingsRepository")
                .hasMessageNotContaining(OwnRepositoryService.class.getName())
                .hasMessageNotContaining(LeakyController.class.getName());
    }

    @Test
    void featureIsTheFirstPackageBelowTheRoot() {
        JavaClasses classes = new ClassFileImporter().importClasses(CrossFeatureService.class, String.class);

        assertThat(ArchitectureRules.featureOf(classes.get(CrossFeatureService.class)))
                .isEqualTo("archfixture");
        assertThat(ArchitectureRules.featureOf(classes.get(String.class))).isNull();
    }
}
