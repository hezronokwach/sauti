package ke.sauti.hms.staff;

import java.util.Map;
import ke.sauti.hms.audit.AuditAction;
import ke.sauti.hms.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Gets the first person in: while the staff table is empty, creates an admin from
 * {@link FirstAdminProperties}. Once anyone exists it does nothing, so restarting never
 * adds a second admin or resets a password. Like every new account, the admin changes
 * the password and sets up an authenticator at first login.
 */
@Component
@EnableConfigurationProperties(FirstAdminProperties.class)
public class FirstAdminSetup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FirstAdminSetup.class);

    private final StaffRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final FirstAdminProperties properties;

    public FirstAdminSetup(
            StaffRepository repository,
            PasswordEncoder passwordEncoder,
            AuditService audit,
            FirstAdminProperties properties) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        if (!StringUtils.hasText(properties.email()) || !StringUtils.hasText(properties.password())) {
            log.warn("There are no staff accounts and FIRST_ADMIN_EMAIL or FIRST_ADMIN_PASSWORD is not set,"
                    + " so nobody can log in. Set both and restart to create the first admin.");
            return;
        }
        if (properties.password().length() < Staff.MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "FIRST_ADMIN_PASSWORD must be at least " + Staff.MIN_PASSWORD_LENGTH + " characters");
        }
        String name = StringUtils.hasText(properties.name()) ? properties.name() : "Administrator";

        Staff admin = repository.save(new Staff(
                name, properties.email(), null, StaffRole.ADMIN, null, passwordEncoder.encode(properties.password())));
        audit.record(
                AuditAction.STAFF_CREATED,
                Staff.class.getSimpleName(),
                admin.getId(),
                null,
                null,
                Map.of("role", StaffRole.ADMIN.name(), "source", "FIRST_ADMIN"));
        log.info("Created the first admin account; it must change its password at first login");
    }
}
