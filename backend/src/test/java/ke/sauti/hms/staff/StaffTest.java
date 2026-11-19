package ke.sauti.hms.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StaffTest {

    private static Staff receptionist() {
        return new Staff("Wanjiru Kamau", "wanjiru@example.test", null, StaffRole.RECEPTIONIST, null, "first-hash");
    }

    @Test
    void newStaffAreActiveAndMustChangeTheirPassword() {
        Staff staff = receptionist();

        assertThat(staff.getStatus()).isEqualTo(StaffStatus.ACTIVE);
        assertThat(staff.isMustChangePassword()).isTrue();
    }

    @Test
    void disablingStopsTheAccount() {
        Staff staff = receptionist();

        staff.disable();

        assertThat(staff.getStatus()).isEqualTo(StaffStatus.DISABLED);
    }

    @Test
    void changingThePasswordReplacesTheHashAndEndsTheForcedChange() {
        Staff staff = receptionist();

        staff.changePassword("new-hash");

        assertThat(staff.getPasswordHash()).isEqualTo("new-hash");
        assertThat(staff.isMustChangePassword()).isFalse();
    }

    @Test
    void aPasswordHashIsRequired() {
        Staff staff = receptionist();

        assertThatThrownBy(() -> staff.changePassword(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void startingAuthenticatorSetupStoresTheSecretButDoesNotRequireCodesYet() {
        Staff staff = receptionist();

        staff.startAuthenticatorSetup("JBSWY3DPEHPK3PXP");

        assertThat(staff.getMfaSecret()).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(staff.isMfaEnabled()).isFalse();
    }

    @Test
    void onlyDoctorsAndNursesNeedALicence() {
        assertThat(StaffRole.DOCTOR.requiresLicence()).isTrue();
        assertThat(StaffRole.NURSE.requiresLicence()).isTrue();
        assertThat(StaffRole.RECEPTIONIST.requiresLicence()).isFalse();
        assertThat(StaffRole.CASHIER.requiresLicence()).isFalse();
        assertThat(StaffRole.ADMIN.requiresLicence()).isFalse();
    }
}
