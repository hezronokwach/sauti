package ke.sauti.hms.prereg;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import ke.sauti.hms.audit.AuditActor;
import ke.sauti.hms.prereg.dto.PreRegisterRequest;
import ke.sauti.hms.prereg.dto.PreRegisteredResponse;
import ke.sauti.hms.prereg.dto.PreRegistrationResponse;
import ke.sauti.hms.staff.StaffRole;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The public pre-registration form, and reception opening an entry by its ticket code. */
@RestController
public class PreRegistrationController {

    private final PreRegistrationService service;

    public PreRegistrationController(PreRegistrationService service) {
        this.service = service;
    }

    /** No login. Answers with the ticket code only, never with any stored record. */
    @PostMapping("/api/public/pre-registrations")
    @ResponseStatus(HttpStatus.CREATED)
    public PreRegisteredResponse submit(@Valid @RequestBody PreRegisterRequest request, HttpServletRequest http) {
        return new PreRegisteredResponse(service.submit(request, http.getRemoteAddr()));
    }

    @GetMapping("/api/pre-registrations/{ticketCode}")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public PreRegistrationResponse forDesk(@PathVariable String ticketCode, @AuthenticationPrincipal AuditActor staff) {
        return PreRegistrationResponse.of(
                service.forDesk(ticketCode, staff.actorId(), StaffRole.valueOf(staff.actorRole())));
    }
}
