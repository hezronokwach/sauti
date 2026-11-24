package ke.sauti.hms.patient;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import ke.sauti.hms.audit.AuditActor;
import ke.sauti.hms.patient.dto.AllergyResponse;
import ke.sauti.hms.patient.dto.EnteredInErrorRequest;
import ke.sauti.hms.patient.dto.RecordAllergyRequest;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.visit.VisitService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A patient's allergies, for the nurses and doctors looking after the patient: the role
 * here, the care team in {@link ke.sauti.hms.visit.AccessPolicy}.
 */
@RestController
@RequestMapping("/api/patients/{patientId}/allergies")
@PreAuthorize("hasAnyRole('NURSE', 'DOCTOR')")
public class AllergyController {

    private final AllergyService service;

    public AllergyController(AllergyService service) {
        this.service = service;
    }

    private static VisitService.Actor actor(AuditActor staff) {
        return new VisitService.Actor(staff.actorId(), StaffRole.valueOf(staff.actorRole()));
    }

    @GetMapping
    public List<AllergyResponse> list(@PathVariable UUID patientId, @AuthenticationPrincipal AuditActor staff) {
        return service.list(patientId, actor(staff)).stream()
                .map(AllergyResponse::of)
                .toList();
    }

    @PostMapping
    public AllergyResponse record(
            @PathVariable UUID patientId,
            @Valid @RequestBody RecordAllergyRequest request,
            @AuthenticationPrincipal AuditActor staff) {
        return AllergyResponse.of(service.record(patientId, request, actor(staff)));
    }

    @PostMapping("/{allergyId}/entered-in-error")
    public AllergyResponse markEnteredInError(
            @PathVariable UUID patientId,
            @PathVariable UUID allergyId,
            @Valid @RequestBody EnteredInErrorRequest request,
            @AuthenticationPrincipal AuditActor staff) {
        return AllergyResponse.of(service.markEnteredInError(patientId, allergyId, request.reason(), actor(staff)));
    }
}
