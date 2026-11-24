package ke.sauti.hms.note.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ke.sauti.hms.note.ClinicalNote;
import ke.sauti.hms.note.NoteAddendum;
import ke.sauti.hms.note.NoteSections;
import ke.sauti.hms.note.NoteStatus;
import org.jspecify.annotations.Nullable;

/**
 * A visit's note with its addenda.
 *
 * @param id the note id
 * @param visitId the visit
 * @param patientId the patient
 * @param authorId the doctor who started it
 * @param status DRAFT or SIGNED
 * @param version send this back with the next save or the sign
 * @param presentingComplaint why the patient came
 * @param history history of the complaint
 * @param examination examination findings
 * @param assessment assessment or diagnosis
 * @param plan treatment, tests and follow-up
 * @param aiAssisted whether AI help was used
 * @param updatedAt last saved
 * @param signedAt when signed, if signed
 * @param signedBy who signed, if signed
 * @param addenda additions after signing, oldest first
 */
public record NoteResponse(
        UUID id,
        UUID visitId,
        UUID patientId,
        UUID authorId,
        NoteStatus status,
        long version,
        @Nullable String presentingComplaint,
        @Nullable String history,
        @Nullable String examination,
        @Nullable String assessment,
        @Nullable String plan,
        boolean aiAssisted,
        Instant updatedAt,
        @Nullable Instant signedAt,
        @Nullable UUID signedBy,
        List<AddendumResponse> addenda) {

    public static NoteResponse of(ClinicalNote note, List<NoteAddendum> addenda) {
        NoteSections s = note.sections();
        return new NoteResponse(
                note.getId(),
                note.getVisitId(),
                note.getPatientId(),
                note.getAuthorId(),
                note.getStatus(),
                note.getVersion(),
                s.presentingComplaint(),
                s.history(),
                s.examination(),
                s.assessment(),
                s.plan(),
                note.isAiAssisted(),
                note.getUpdatedAt(),
                note.getSignedAt(),
                note.getSignedBy(),
                addenda.stream().map(AddendumResponse::of).toList());
    }
}
