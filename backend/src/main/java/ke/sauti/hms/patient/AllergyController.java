package ke.sauti.hms.patient;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import ke.sauti.hms.audit.AuditActor;
import ke.sauti.hms.patient.dto.AllergyResponse;
import ke.sauti.hms.patient.dto.EnteredInErrorRequest;
import ke.sauti.hms.patient.dto.RecordAllergyRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A patient's allergies, for the clinicians (nurses and doctors). Issue 18 adds the
 * care-team check on top of the role.
 */
@RestController
@RequestMapping("/api/patients/{patientId}/allergies")
@PreAuthorize("hasAnyRole('NURSE', 'DOCTOR')")
public class AllergyController {

    private final AllergyService service;

    public AllergyController(AllergyService service) {
        this.service = service;
    }

    @GetMapping
    public List<AllergyResponse> list(@PathVariable UUID patientId) {
        return service.list(patientId).stream().map(AllergyResponse::of).toList();
    }

    @PostMapping
    public AllergyResponse record(
            @PathVariable UUID patientId,
            @Valid @RequestBody RecordAllergyRequest request,
            @AuthenticationPrincipal AuditActor staff) {
        return AllergyResponse.of(service.record(patientId, request, staff.actorId()));
    }

    @PostMapping("/{allergyId}/entered-in-error")
    public AllergyResponse markEnteredInError(
            @PathVariable UUID patientId,
            @PathVariable UUID allergyId,
            @Valid @RequestBody EnteredInErrorRequest request,
            @AuthenticationPrincipal AuditActor staff) {
        return AllergyResponse.of(service.markEnteredInError(patientId, allergyId, request.reason(), staff.actorId()));
    }
}
