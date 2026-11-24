package ke.sauti.hms.security;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import ke.sauti.hms.audit.AuditAction;
import ke.sauti.hms.audit.AuditService;
import ke.sauti.hms.common.error.BusinessRuleException;
import ke.sauti.hms.common.error.ErrorCode;
import ke.sauti.hms.security.totp.TotpGenerator;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.staff.StaffService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two login steps, the password change and logout. Each step returns its outcome
 * instead of throwing on a refusal, so the failure count and the {@code LOGIN_FAILED}
 * audit row commit; the controller turns a refusal into the error response afterwards.
 */
@Service
public class LoginService {

    private static final String STAFF = "Staff";

    private final StaffService staffService;
    private final PasswordEncoder passwordEncoder;
    private final TotpGenerator totp;
    private final CodeVerifier codeVerifier;
    private final AuditService audit;
    private final Clock clock;

    /** Checked when the email is unknown, so the reply takes as long as for a real account. */
    private final String dummyHash;

    public LoginService(
            StaffService staffService,
            PasswordEncoder passwordEncoder,
            TotpGenerator totp,
            CodeVerifier codeVerifier,
            AuditService audit,
            Clock clock) {
        this.staffService = staffService;
        this.passwordEncoder = passwordEncoder;
        this.totp = totp;
        this.codeVerifier = codeVerifier;
        this.audit = audit;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("not-a-password-" + UUID.randomUUID());
    }

    @Transactional
    public PasswordStep checkPassword(String email, String password) {
        Optional<Staff> found = staffService.findByEmail(email);
        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            audit.record(AuditAction.LOGIN_FAILED, failure("password", "UNKNOWN_EMAIL"));
            return new PasswordStep.Failed();
        }
        Staff staff = found.get();
        Instant now = clock.instant();
        boolean passwordMatches = passwordEncoder.matches(password, staff.getPasswordHash());

        if (!staff.isActive()) {
            return failed(staff, "password", "DISABLED");
        }
        if (staff.isLocked(now)) {
            return failed(staff, "password", "LOCKED");
        }
        if (!passwordMatches) {
            boolean locked = staff.recordFailedLogin(now);
            return failed(staff, "password", locked ? "WRONG_PASSWORD_NOW_LOCKED" : "WRONG_PASSWORD");
        }
        if (staff.isMfaEnabled()) {
            return new PasswordStep.CodeRequired(staff.getId());
        }
        if (staff.getMfaSecret() == null) {
            staff.startAuthenticatorSetup(totp.newSecret());
        }
        return new PasswordStep.SetupRequired(staff.getId(), totp.setupUri(staff.getEmail(), staff.getMfaSecret()));
    }

    /**
     * Checks the code for a pending login. On success the person is put into the current
     * security context (so {@code LOGIN_OK} records them as the actor); saving that
     * context into the session is the caller's job.
     */
    @Transactional
    public CodeStep checkCode(PendingLogin pending, String code) {
        Optional<Staff> found = staffService.findById(pending.staffId());
        if (found.isEmpty()) {
            audit.record(AuditAction.LOGIN_FAILED, failure("code", "UNKNOWN_STAFF"));
            return new CodeStep.Failed(true);
        }
        Staff staff = found.get();
        Instant now = clock.instant();
        if (!staff.isActive()) {
            recordFailure(staff, "code", "DISABLED");
            return new CodeStep.Failed(true);
        }
        if (staff.isLocked(now)) {
            recordFailure(staff, "code", "LOCKED");
            return new CodeStep.Failed(true);
        }
        if (!codeVerifier.verify(staff, code)) {
            boolean locked = staff.recordFailedLogin(now);
            recordFailure(staff, "code", locked ? "WRONG_CODE_NOW_LOCKED" : "WRONG_CODE");
            return new CodeStep.Failed(locked);
        }
        if (pending.setup()) {
            staff.confirmAuthenticator();
        }
        staff.recordSuccessfulLogin();

        StaffPrincipal principal = StaffPrincipal.of(staff);
        putInSecurityContext(principal);
        audit.record(
                AuditAction.LOGIN_OK,
                STAFF,
                staff.getId(),
                null,
                null,
                pending.setup() ? Map.of("authenticator", "CONFIRMED") : null);
        return new CodeStep.LoggedIn(principal);
    }

    /**
     * Replaces the logged-in person's password. The new principal (no longer required to
     * change it) is put into the current security context.
     */
    @Transactional
    public StaffPrincipal changePassword(StaffPrincipal current, String currentPassword, String newPassword) {
        Staff staff =
                staffService.findById(current.id()).orElseThrow(() -> new LoginException(ErrorCode.UNAUTHENTICATED));
        if (!passwordEncoder.matches(currentPassword, staff.getPasswordHash())) {
            throw new BusinessRuleException("The current password is not correct");
        }
        if (passwordEncoder.matches(newPassword, staff.getPasswordHash())) {
            throw new BusinessRuleException("Choose a password different from the current one");
        }
        staff.changePassword(passwordEncoder.encode(newPassword));
        audit.record(AuditAction.PASSWORD_CHANGED, STAFF, staff.getId(), null, null, null);

        StaffPrincipal updated = StaffPrincipal.of(staff);
        putInSecurityContext(updated);
        return updated;
    }

    /** Records the logout while the person is still in the security context. */
    @Transactional
    public void recordLogout(StaffPrincipal principal) {
        audit.record(AuditAction.LOGOUT, STAFF, principal.id(), null, null, null);
    }

    private PasswordStep failed(Staff staff, String stage, String reason) {
        recordFailure(staff, stage, reason);
        return new PasswordStep.Failed();
    }

    private void recordFailure(Staff staff, String stage, String reason) {
        audit.record(AuditAction.LOGIN_FAILED, STAFF, staff.getId(), null, null, failure(stage, reason));
    }

    private static Map<String, String> failure(String stage, String reason) {
        return Map.of("stage", stage, "reason", reason);
    }

    private static void putInSecurityContext(StaffPrincipal principal) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority(principal.authority()))));
        SecurityContextHolder.setContext(context);
    }
}
