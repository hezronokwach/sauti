package ke.sauti.hms.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import ke.sauti.hms.audit.AuditAction;
import ke.sauti.hms.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(OutputCaptureExtension.class)
class FirstAdminSetupTest {

    private final StaffRepository repository = mock(StaffRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AuditService audit = mock(AuditService.class);

    private void run(FirstAdminProperties properties) {
        new FirstAdminSetup(repository, encoder, audit, properties).run(new DefaultApplicationArguments());
    }

    private static FirstAdminProperties admin(String password) {
        return new FirstAdminProperties(" Admin@Example.TEST ", password, "Wanjiku Admin");
    }

    @Test
    void anEmptyStaffTableGetsOneAdminWhoMustChangeThePassword() {
        when(repository.count()).thenReturn(0L);
        when(encoder.encode("First-Admin-2026")).thenReturn("hash");
        when(repository.save(any(Staff.class))).thenAnswer(call -> call.getArgument(0));

        run(admin("First-Admin-2026"));

        ArgumentCaptor<Staff> saved = ArgumentCaptor.forClass(Staff.class);
        verify(repository).save(saved.capture());
        Staff staff = saved.getValue();
        assertThat(staff.getRole()).isEqualTo(StaffRole.ADMIN);
        assertThat(staff.getEmail()).isEqualTo("admin@example.test");
        assertThat(staff.getFullName()).isEqualTo("Wanjiku Admin");
        assertThat(staff.getPasswordHash()).isEqualTo("hash");
        assertThat(staff.isMustChangePassword()).isTrue();
        assertThat(staff.isMfaEnabled()).isFalse();
        verify(audit)
                .record(
                        eq(AuditAction.STAFF_CREATED),
                        eq("Staff"),
                        any(),
                        any(),
                        any(),
                        eq(Map.of("role", "ADMIN", "source", "FIRST_ADMIN")));
    }

    @Test
    void existingStaffMeanNothingIsCreated() {
        when(repository.count()).thenReturn(3L);

        run(admin("First-Admin-2026"));

        verify(repository, never()).save(any());
        verify(encoder, never()).encode(anyString());
    }

    @Test
    void withoutTheVariablesItWarnsAndStartsAnyway(CapturedOutput output) {
        when(repository.count()).thenReturn(0L);

        run(new FirstAdminProperties("", "", "Administrator"));

        verify(repository, never()).save(any());
        assertThat(output).contains("FIRST_ADMIN_EMAIL or FIRST_ADMIN_PASSWORD is not set");
    }

    @Test
    void aShortPasswordStopsStartup() {
        when(repository.count()).thenReturn(0L);

        assertThatThrownBy(() -> run(admin("too-short")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 12 characters")
                .hasMessageNotContaining("too-short");
        verify(repository, never()).save(any());
    }
}
