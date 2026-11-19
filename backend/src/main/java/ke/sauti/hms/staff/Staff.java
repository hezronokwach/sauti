package ke.sauti.hms.staff;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import ke.sauti.hms.common.crypto.EncryptedStringConverter;
import ke.sauti.hms.common.persistence.BaseEntity;

/**
 * A person who logs in. Login state (failed attempts, lock, authenticator) lives here;
 * the login flow itself arrives in issues 11 and 12.
 */
@Entity
@Table(name = "staff")
public class Staff extends BaseEntity {

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StaffRole role;

    @Column(name = "licence_number", length = 50)
    private String licenceNumber;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** Authenticator secret (Base32); encrypted in the column, plain text here. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "mfa_secret")
    private String mfaSecret;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled;

    @Column(name = "last_code_step")
    private Long lastCodeStep;

    @Column(name = "failed_logins", nullable = false)
    private int failedLogins;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StaffStatus status = StaffStatus.ACTIVE;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = true;

    protected Staff() {
        // for JPA
    }

    public Staff(
            String fullName, String email, String phone, StaffRole role, String licenceNumber, String passwordHash) {
        this.fullName = Objects.requireNonNull(fullName, "fullName");
        this.email = normaliseEmail(email);
        this.phone = phone;
        this.role = Objects.requireNonNull(role, "role");
        this.licenceNumber = licenceNumber;
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
    }

    /** Emails are compared and stored trimmed and lower-case. */
    public static String normaliseEmail(String email) {
        return Objects.requireNonNull(email, "email").trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Stops the account from logging in. Ending the person's open sessions is the
     * caller's job (issue 14), since sessions live outside this entity.
     */
    public void disable() {
        this.status = StaffStatus.DISABLED;
    }

    /** Replaces the password hash; a freely chosen password ends the forced change. */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "newPasswordHash");
        this.mustChangePassword = false;
    }

    /**
     * Stores a newly generated authenticator secret. Codes are not required until the
     * person proves the authenticator works (issue 11 turns {@code mfaEnabled} on).
     */
    public void startAuthenticatorSetup(String secret) {
        this.mfaSecret = Objects.requireNonNull(secret, "secret");
    }

    public String getMfaSecret() {
        return mfaSecret;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public StaffRole getRole() {
        return role;
    }

    public String getLicenceNumber() {
        return licenceNumber;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public Long getLastCodeStep() {
        return lastCodeStep;
    }

    public int getFailedLogins() {
        return failedLogins;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public StaffStatus getStatus() {
        return status;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }
}
