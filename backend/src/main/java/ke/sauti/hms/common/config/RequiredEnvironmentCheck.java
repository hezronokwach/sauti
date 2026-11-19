package ke.sauti.hms.common.config;

import java.util.List;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

/**
 * Stops startup when an environment variable listed in {@code sauti.required-env}
 * is missing or blank, naming every missing variable at once.
 * <p>
 * Runs after the profile-specific configuration files are loaded and before any
 * bean is created, so a missing database URL is reported here rather than as an
 * unresolved placeholder deep inside the datasource setup.
 */
public class RequiredEnvironmentCheck implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY = "sauti.required-env";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List<String> required = Binder.get(environment)
                .bind(PROPERTY, Bindable.listOf(String.class))
                .orElse(List.of());

        List<String> missing = required.stream()
                .filter(name -> !StringUtils.hasText(environment.getProperty(name)))
                .toList();

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Missing required environment variables for active profiles "
                            + List.of(environment.getActiveProfiles()) + ": "
                            + String.join(", ", missing)
                            + ". Set them in the host environment; every name is listed in .env.example.");
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
