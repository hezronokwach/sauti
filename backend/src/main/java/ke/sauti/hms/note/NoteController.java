package ke.sauti.hms.note;

import jakarta.validation.Valid;
import java.util.UUID;
import ke.sauti.hms.audit.AuditActor;
import ke.sauti.hms.note.dto.AddAddendumRequest;
import ke.sauti.hms.note.dto.NoteResponse;
import ke.sauti.hms.note.dto.SaveNoteRequest;
import ke.sauti.hms.note.dto.SignNoteRequest;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.visit.VisitService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A visit's note: doctors draft, sign and add addenda; nurses on the care team read it. */
@RestController
@RequestMapping("/api/visits/{visitId}/note")
public class NoteController {

    private final NoteService service;

    public NoteController(NoteService service) {
        this.service = service;
    }

    static VisitService.Actor actor(AuditActor staff) {
        return new VisitService.Actor(staff.actorId(), StaffRole.valueOf(staff.actorRole()));
    }

    private static NoteResponse respond(NoteWithAddenda note) {
        return NoteResponse.of(note.note(), note.addenda());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR')")
    public NoteResponse get(@PathVariable UUID visitId, @AuthenticationPrincipal AuditActor staff) {
        return respond(service.get(visitId, actor(staff)));
    }

    @PutMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public NoteResponse save(
            @PathVariable UUID visitId,
            @Valid @RequestBody SaveNoteRequest request,
            @AuthenticationPrincipal AuditActor staff) {
        return respond(service.saveDraft(visitId, request, actor(staff)));
    }

    @PostMapping("/sign")
    @PreAuthorize("hasRole('DOCTOR')")
    public NoteResponse sign(
            @PathVariable UUID visitId,
            @Valid @RequestBody SignNoteRequest request,
            @AuthenticationPrincipal AuditActor staff) {
        return respond(service.sign(visitId, request.version(), request.code(), actor(staff)));
    }

    @PostMapping("/addenda")
    @PreAuthorize("hasRole('DOCTOR')")
    public NoteResponse addAddendum(
            @PathVariable UUID visitId,
            @Valid @RequestBody AddAddendumRequest request,
            @AuthenticationPrincipal AuditActor staff) {
        return respond(service.addAddendum(visitId, request.text(), actor(staff)));
    }
}
