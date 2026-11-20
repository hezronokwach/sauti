package ke.sauti.hms.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import ke.sauti.hms.security.StaffPrincipal;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.staff.StaffRepository;
import ke.sauti.hms.staff.StaffRole;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Requests made as a real staff member without going through the login flow: the
 * person is saved (audit rows point at real staff) and the request carries their
 * principal and a valid CSRF cookie and header.
 */
public final class ActingAs {

    private ActingAs() {}

    /** Saves a ready-to-work staff member with this role (no one-time password). */
    public static Staff staff(StaffRepository repository, StaffRole role) {
        Staff staff = new Staff(
                "Test " + role,
                role.name().toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID() + "@example.test",
                null,
                role,
                role.requiresLicence() ? "LIC-1" : null,
                "not-used");
        staff.changePassword("not-used");
        return repository.save(staff);
    }

    /** Sends the request as this person. */
    public static RequestPostProcessor as(Staff staff) {
        StaffPrincipal principal = StaffPrincipal.of(staff);
        RequestPostProcessor login = authentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority(principal.authority()))));
        RequestPostProcessor csrf = ApiBrowser.validCsrfToken();
        return request -> csrf.postProcessRequest(login.postProcessRequest(request));
    }
}
