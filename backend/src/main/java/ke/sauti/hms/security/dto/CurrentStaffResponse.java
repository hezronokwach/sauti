package ke.sauti.hms.security.dto;

import java.util.UUID;
import ke.sauti.hms.security.StaffPrincipal;
import ke.sauti.hms.staff.StaffRole;

/**
 * The logged-in staff member, as the front end sees them. Returned by
 * {@code GET /api/auth/me} (called on every page load), and after the code step and a
 * password change.
 *
 * @param id the staff id
 * @param fullName shown in the interface
 * @param email the login email
 * @param role decides which screens are shown
 * @param mustChangePassword whether only the password change is allowed until it is done
 */
public record CurrentStaffResponse(UUID id, String fullName, String email, StaffRole role, boolean mustChangePassword) {

    public static CurrentStaffResponse of(StaffPrincipal principal) {
        return new CurrentStaffResponse(
                principal.id(),
                principal.fullName(),
                principal.email(),
                principal.role(),
                principal.mustChangePassword());
    }
}
