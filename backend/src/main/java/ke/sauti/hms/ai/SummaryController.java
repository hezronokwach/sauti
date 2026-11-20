package ke.sauti.hms.ai;

import jakarta.validation.Valid;
import java.util.UUID;
import ke.sauti.hms.ai.dto.AcceptClaimsRequest;
import ke.sauti.hms.ai.dto.SummaryResponse;
import ke.sauti.hms.audit.AuditActor;
import ke.sauti.hms.note.NoteWithAddenda;
import ke.sauti.hms.note.dto.NoteResponse;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.visit.VisitService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The doctor asks for a summary of the consultation transcript, and accepts checked claims into the note. */
@RestController
public class SummaryController {

    private final SummaryService service;
    private final ClaimAcceptService accepting;

    public SummaryController(SummaryService service, ClaimAcceptService accepting) {
        this.service = service;
        this.accepting = accepting;
    }

    private static VisitService.Actor actor(AuditActor staff) {
        return new VisitService.Actor(staff.actorId(), StaffRole.valueOf(staff.actorRole()));
    }

    @PostMapping("/api/visits/{visitId}/summary")
    @PreAuthorize("hasRole('DOCTOR')")
    public SummaryResponse summarise(@PathVariable UUID visitId, @AuthenticationPrincipal AuditActor staff) {
        return SummaryResponse.of(service.summarise(visitId, actor(staff)));
    }

    /** Brings checked claims into the note; returns the note as it now is. */
    @PostMapping("/api/visits/{visitId}/summary/{requestId}/accept")
    @PreAuthorize("hasRole('DOCTOR')")
    public NoteResponse accept(
            @PathVariable UUID visitId,
            @PathVariable UUID requestId,
            @Valid @RequestBody AcceptClaimsRequest request,
            @AuthenticationPrincipal AuditActor staff) {
        NoteWithAddenda note =
                accepting.accept(visitId, requestId, request.claims(), request.noteVersion(), actor(staff));
        return NoteResponse.of(note.note(), note.addenda());
    }
}
