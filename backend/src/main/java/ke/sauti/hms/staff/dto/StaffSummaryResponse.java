package ke.sauti.hms.staff.dto;

import java.time.Instant;
import java.util.UUID;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.staff.StaffStatus;
import org.jspecify.annotations.Nullable;

/**
 * One staff account as the admin screens show it. Never includes the password hash or
 * the authenticator secret.
 *
 * @param id the staff id
 * @param fullName display name
 * @param email login email
 * @param phone contact number, if any
 * @param role the person's role
 * @param licenceNumber for doctors and nurses
 * @param status ACTIVE or DISABLED
 * @param authenticatorSetUp whether the person has finished setting up an authenticator
 * @param mustChangePassword whether the person still has a one-time password
 * @param lockedUntil set while the account is locked after failed logins
 */
public record StaffSummaryResponse(
        UUID id,
        String fullName,
        String email,
        @Nullable String phone,
        StaffRole role,
        @Nullable String licenceNumber,
        StaffStatus status,
        boolean authenticatorSetUp,
        boolean mustChangePassword,
        @Nullable Instant lockedUntil) {

    public static StaffSummaryResponse of(Staff staff) {
        return new StaffSummaryResponse(
                staff.getId(),
                staff.getFullName(),
                staff.getEmail(),
                staff.getPhone(),
                staff.getRole(),
                staff.getLicenceNumber(),
                staff.getStatus(),
                staff.isMfaEnabled(),
                staff.isMustChangePassword(),
                staff.getLockedUntil());
    }
}
