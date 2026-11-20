package ke.sauti.hms.ai;

import java.util.UUID;
import ke.sauti.hms.ai.dto.SummaryResponse;
import ke.sauti.hms.audit.AuditActor;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.visit.VisitService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** The doctor asks for a summary of the consultation transcript. */
@RestController
public class SummaryController {

    private final SummaryService service;

    public SummaryController(SummaryService service) {
        this.service = service;
    }

    @PostMapping("/api/visits/{visitId}/summary")
    @PreAuthorize("hasRole('DOCTOR')")
    public SummaryResponse summarise(@PathVariable UUID visitId, @AuthenticationPrincipal AuditActor staff) {
        return SummaryResponse.of(service.summarise(
                visitId, new VisitService.Actor(staff.actorId(), StaffRole.valueOf(staff.actorRole()))));
    }
}
