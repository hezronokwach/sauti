package ke.sauti.hms.ai;

import java.util.List;
import java.util.UUID;
import ke.sauti.hms.ai.dto.FieldDraftResponse;
import ke.sauti.hms.audit.AuditActor;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.visit.VisitService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Voice drafts for a visit's reception fields. */
@RestController
@RequestMapping("/api/visits/{visitId}/field-drafts")
public class FieldDraftController {

    private final FieldDraftService service;

    public FieldDraftController(FieldDraftService service) {
        this.service = service;
    }

    private static VisitService.Actor actor(AuditActor staff) {
        return new VisitService.Actor(staff.actorId(), StaffRole.valueOf(staff.actorRole()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'NURSE')")
    public List<FieldDraftResponse> pending(@PathVariable UUID visitId, @AuthenticationPrincipal AuditActor staff) {
        return service.pending(visitId, actor(staff)).stream()
                .map(FieldDraftResponse::of)
                .toList();
    }

    /** Recorded after the person saved the value through the normal form. */
    @PostMapping("/{draftId}/confirm")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'NURSE')")
    public FieldDraftResponse confirm(
            @PathVariable UUID visitId, @PathVariable UUID draftId, @AuthenticationPrincipal AuditActor staff) {
        return FieldDraftResponse.of(service.decide(visitId, draftId, true, actor(staff)));
    }

    @PostMapping("/{draftId}/dismiss")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'NURSE')")
    public FieldDraftResponse dismiss(
            @PathVariable UUID visitId, @PathVariable UUID draftId, @AuthenticationPrincipal AuditActor staff) {
        return FieldDraftResponse.of(service.decide(visitId, draftId, false, actor(staff)));
    }
}
