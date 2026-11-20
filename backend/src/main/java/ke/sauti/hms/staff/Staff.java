package ke.sauti.hms.staff;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
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

    /** Wrong passwords or codes in a row before the account locks. */
    public static final int MAX_FAILED_LOGINS = 5;

    /** How long a locked account stays locked. */
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

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

    public boolean isActive() {
        return status == StaffStatus.ACTIVE;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /**
     * Counts a wrong password or code. The fifth in a row locks the account for
     * {@link #LOCK_DURATION} and starts the count again, so after the lock ends the
     * person has another {@value #MAX_FAILED_LOGINS} tries.
     *
     * @return whether this failure locked the account
     */
    public boolean recordFailedLogin(Instant now) {
        failedLogins++;
        if (failedLogins < MAX_FAILED_LOGINS) {
            return false;
        }
        failedLogins = 0;
        lockedUntil = now.plus(LOCK_DURATION);
        return true;
    }

    /** A completed login clears the failure count and any expired lock. */
    public void recordSuccessfulLogin() {
        failedLogins = 0;
        lockedUntil = null;
    }

    /** Turns codes on once the person has entered a first correct code from the new authenticator. */
    public void confirmAuthenticator() {
        if (mfaSecret == null) {
            throw new IllegalStateException("No authenticator secret to confirm");
        }
        this.mfaEnabled = true;
    }

    /**
     * Records the time step of a correct code, refusing any step not newer than the last
     * one used, so each code works only once. Two concurrent uses of the same code both
     * try to save this entity; the version check fails the second.
     *
     * @return whether the step was newer and is now recorded
     */
    public boolean acceptCodeStep(long step) {
        if (lastCodeStep != null && step <= lastCodeStep) {
            return false;
        }
        this.lastCodeStep = step;
        return true;
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
