package ke.sauti.hms.staff;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The first admin, created at startup only while the staff table is empty. Read from
 * {@code FIRST_ADMIN_EMAIL}, {@code FIRST_ADMIN_PASSWORD} and {@code FIRST_ADMIN_NAME}.
 *
 * @param email the admin's login email; blank means no first admin is configured
 * @param password the one-time password, changed at first login
 * @param name the admin's display name
 */
@ConfigurationProperties(prefix = "sauti.first-admin")
public record FirstAdminProperties(String email, String password, String name) {}
