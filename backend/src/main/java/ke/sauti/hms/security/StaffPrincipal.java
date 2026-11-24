package ke.sauti.hms.security;

import java.io.Serializable;
import java.util.UUID;
import ke.sauti.hms.audit.AuditActor;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.staff.StaffRole;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * The logged-in staff member as the session keeps them: small, serialisable, and free of
 * secrets. Its name is the staff id, so Spring Session indexes each session by staff id
 * and an account's sessions can be found and ended when it is disabled.
 *
 * @param id the staff id
 * @param fullName shown in the interface
 * @param email the login email
 * @param role the one role this person has
 * @param mustChangePassword whether only the password change is allowed until it is done
 */
public record StaffPrincipal(UUID id, String fullName, String email, StaffRole role, boolean mustChangePassword)
        implements AuditActor, AuthenticatedPrincipal, Serializable {

    public static StaffPrincipal of(Staff staff) {
        return new StaffPrincipal(
                staff.getId(), staff.getFullName(), staff.getEmail(), staff.getRole(), staff.isMustChangePassword());
    }

    /** The Spring Security authority for this person's role, for example {@code ROLE_DOCTOR}. */
    public String authority() {
        return "ROLE_" + role.name();
    }

    @Override
    public String getName() {
        return id.toString();
    }

    @Override
    public UUID actorId() {
        return id;
    }

    @Override
    public String actorRole() {
        return role.name();
    }
}
