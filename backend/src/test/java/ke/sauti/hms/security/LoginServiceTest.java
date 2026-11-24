package ke.sauti.hms.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import ke.sauti.hms.audit.AuditAction;
import ke.sauti.hms.audit.AuditService;
import ke.sauti.hms.security.totp.TotpGenerator;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.staff.StaffService;
import ke.sauti.hms.support.FixedClockConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class LoginServiceTest {

    private final StaffService staffService = mock(StaffService.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AuditService audit = mock(AuditService.class);
    private final Clock clock = Clock.fixed(FixedClockConfiguration.NOW, ZoneOffset.UTC);
    private final TotpGenerator totp = new TotpGenerator(clock);

    private LoginService service() {
        when(encoder.encode(anyString())).thenReturn("dummy-hash");
        return new LoginService(staffService, encoder, totp, new CodeVerifier(totp), audit, clock);
    }

    @Test
    void anUnknownEmailStillChecksAPasswordAgainstTheDummyHash() {
        LoginService service = service();
        when(staffService.findByEmail("nobody@example.test")).thenReturn(Optional.empty());

        PasswordStep step = service.checkPassword("nobody@example.test", "guess");

        assertThat(step).isInstanceOf(PasswordStep.Failed.class);
        verify(encoder).matches("guess", "dummy-hash");
        verify(audit).record(AuditAction.LOGIN_FAILED, Map.of("stage", "password", "reason", "UNKNOWN_EMAIL"));
    }

    @Test
    void aDisabledAccountStillChecksItsPasswordSoTimingRevealsNothing() {
        LoginService service = service();
        Staff staff = new Staff("Off Duty", "off@example.test", null, StaffRole.CASHIER, null, "real-hash");
        staff.disable();
        when(staffService.findByEmail("off@example.test")).thenReturn(Optional.of(staff));

        PasswordStep step = service.checkPassword("off@example.test", "guess");

        assertThat(step).isInstanceOf(PasswordStep.Failed.class);
        verify(encoder).matches("guess", "real-hash");
        verify(audit)
                .record(
                        eq(AuditAction.LOGIN_FAILED),
                        eq("Staff"),
                        any(),
                        any(),
                        any(),
                        eq(Map.of("stage", "password", "reason", "DISABLED")));
    }
}
