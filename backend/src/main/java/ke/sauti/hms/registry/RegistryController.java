package ke.sauti.hms.registry;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import ke.sauti.hms.audit.AuditActor;
import ke.sauti.hms.registry.dto.EligibilityResponse;
import ke.sauti.hms.registry.dto.RegistryLookupResponse;
import ke.sauti.hms.staff.StaffRole;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The registry lookup at reception, and the eligibility badge for reception and billing. */
@RestController
@Validated
public class RegistryController {

    private final RegistryService registry;
    private final EligibilityService eligibility;

    public RegistryController(RegistryService registry, EligibilityService eligibility) {
        this.registry = registry;
        this.eligibility = eligibility;
    }

    @GetMapping("/api/registry/lookup")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public RegistryLookupResponse lookup(
            @RequestParam @NotNull IdType idType,
            @RequestParam @Pattern(regexp = "[A-Za-z0-9]{4,20}") String idNumber,
            @AuthenticationPrincipal AuditActor staff) {
        return registry.lookup(idType, idNumber, StaffRole.valueOf(staff.actorRole()))
                .map(RegistryLookupResponse::of)
                .orElseGet(RegistryLookupResponse::notFound);
    }

    @GetMapping("/api/patients/{patientId}/eligibility")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'CASHIER')")
    public EligibilityResponse eligibility(@PathVariable UUID patientId) {
        return eligibility.forPatient(patientId).map(EligibilityResponse::of).orElseGet(EligibilityResponse::none);
    }
}
