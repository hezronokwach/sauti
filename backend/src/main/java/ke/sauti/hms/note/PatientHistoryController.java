package ke.sauti.hms.note;

import java.util.List;
import java.util.UUID;
import ke.sauti.hms.audit.AuditActor;
import ke.sauti.hms.note.dto.NoteResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * A patient's earlier signed notes, for the doctor seeing them now. No old audio or
 * transcripts. Prescriptions join when they exist (issue 41).
 */
@RestController
public class PatientHistoryController {

    private final NoteService service;

    public PatientHistoryController(NoteService service) {
        this.service = service;
    }

    @GetMapping("/api/patients/{patientId}/history")
    @PreAuthorize("hasRole('DOCTOR')")
    public List<NoteResponse> history(@PathVariable UUID patientId, @AuthenticationPrincipal AuditActor staff) {
        return service.history(patientId, NoteController.actor(staff)).stream()
                .map(note -> NoteResponse.of(note.note(), note.addenda()))
                .toList();
    }
}
