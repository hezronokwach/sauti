package ke.sauti.hms.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class RequiredEnvironmentCheckTest {

    private final RequiredEnvironmentCheck check = new RequiredEnvironmentCheck();
    private final SpringApplication application = new SpringApplication();

    @Test
    void passesWhenNothingIsRequired() {
        MockEnvironment environment = new MockEnvironment();

        assertThatCode(() -> check.postProcessEnvironment(environment, application))
                .doesNotThrowAnyException();
    }

    @Test
    void passesWhenEveryRequiredVariableIsSet() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(RequiredEnvironmentCheck.PROPERTY, "SAUTI_A,SAUTI_B")
                .withProperty("SAUTI_A", "a")
                .withProperty("SAUTI_B", "b");

        assertThatCode(() -> check.postProcessEnvironment(environment, application))
                .doesNotThrowAnyException();
    }

    @Test
    void namesEveryMissingOrBlankVariable() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(RequiredEnvironmentCheck.PROPERTY, "SAUTI_A,SAUTI_B,SAUTI_C")
                .withProperty("SAUTI_A", "a")
                .withProperty("SAUTI_C", "  ");
        environment.setActiveProfiles("demo");

        assertThatThrownBy(() -> check.postProcessEnvironment(environment, application))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("[demo]")
                .hasMessageContaining("SAUTI_B, SAUTI_C")
                .hasMessageNotContaining("SAUTI_A");
    }
}
