package ke.sauti.hms.triage;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import ke.sauti.hms.audit.AuditActor;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.triage.dto.RecordVitalsRequest;
import ke.sauti.hms.triage.dto.VitalsResponse;
import ke.sauti.hms.visit.VisitService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Vital signs: nurses record them at triage; nurses and doctors on the care team read them. */
@RestController
@RequestMapping("/api/visits/{visitId}/vitals")
public class VitalsController {

    private final VitalsService service;

    public VitalsController(VitalsService service) {
        this.service = service;
    }

    private static VisitService.Actor actor(AuditActor staff) {
        return new VisitService.Actor(staff.actorId(), StaffRole.valueOf(staff.actorRole()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('NURSE')")
    public VitalsResponse record(
            @PathVariable UUID visitId,
            @Valid @RequestBody RecordVitalsRequest request,
            @AuthenticationPrincipal AuditActor staff) {
        return VitalsResponse.of(service.record(visitId, request, actor(staff)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR')")
    public List<VitalsResponse> list(@PathVariable UUID visitId, @AuthenticationPrincipal AuditActor staff) {
        return service.list(visitId, actor(staff)).stream()
                .map(VitalsResponse::of)
                .toList();
    }
}
