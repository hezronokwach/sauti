package ke.sauti.hms.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Objects;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

/**
 * Layering rules for the backend. The code is organised by feature: each top-level
 * package under {@value #ROOT} (common, facility, staff, patient, visit, ...) owns its
 * controllers, services, repositories and entities.
 */
final class ArchitectureRules {

    static final String ROOT = "ke.sauti.hms";

    /** Controllers handle HTTP and call services; only services touch repositories. */
    static final ArchRule CONTROLLERS_DO_NOT_USE_REPOSITORIES = noClasses()
            .that()
            .areAnnotatedWith(RestController.class)
            .or()
            .areAnnotatedWith(Controller.class)
            .should()
            .dependOnClassesThat()
            .areAssignableTo(Repository.class)
            .because("controllers call services, and services own data access and its checks");

    /**
     * A feature reads and writes only its own tables. To use another feature's data it
     * calls that feature's service, so access checks and audit rows are never bypassed.
     */
    static final ArchRule FEATURES_USE_ONLY_THEIR_OWN_REPOSITORIES = classes()
            .that()
            .resideInAPackage(ROOT + "..")
            .should(useOnlyRepositoriesOfTheirOwnFeature())
            .because("another feature's data is reached through its service, which applies its rules");

    private ArchitectureRules() {}

    private static ArchCondition<JavaClass> useOnlyRepositoriesOfTheirOwnFeature() {
        return new ArchCondition<>("only use repositories of their own feature") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                String originFeature = featureOf(origin);
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    String targetFeature = featureOf(target);
                    boolean appRepository = targetFeature != null && target.isAssignableTo(Repository.class);
                    if (appRepository && !Objects.equals(originFeature, targetFeature)) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    /**
     * The feature a class belongs to: the first package segment below {@value #ROOT},
     * an empty string for classes directly in it, or null for classes outside it.
     */
    static String featureOf(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        if (packageName.equals(ROOT)) {
            return "";
        }
        if (!packageName.startsWith(ROOT + ".")) {
            return null;
        }
        String rest = packageName.substring(ROOT.length() + 1);
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}
