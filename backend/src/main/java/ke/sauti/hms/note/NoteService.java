package ke.sauti.hms.note;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ke.sauti.hms.audit.AuditAction;
import ke.sauti.hms.audit.AuditService;
import ke.sauti.hms.common.error.BusinessRuleException;
import ke.sauti.hms.common.error.ConflictException;
import ke.sauti.hms.common.error.ForbiddenException;
import ke.sauti.hms.common.error.NotFoundException;
import ke.sauti.hms.note.dto.SaveNoteRequest;
import ke.sauti.hms.patient.PatientService;
import ke.sauti.hms.security.CodeCheck;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.staff.StaffService;
import ke.sauti.hms.visit.AccessPolicy;
import ke.sauti.hms.visit.ClinicalData;
import ke.sauti.hms.visit.Visit;
import ke.sauti.hms.visit.VisitService;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The doctor's note for a visit.
 *
 * <ul>
 *   <li>Drafts are saved with the version the editor holds; a different current version is
 *       a 409, so two tabs never overwrite each other.
 *   <li>Signing needs assessment and plan and a fresh authenticator code. After that the
 *       note never changes; saves are refused with 409.
 *   <li>Addenda are added only to signed notes.
 * </ul>
 *
 * Saves and signs take a row lock on the note, so they cannot interleave.
 */
@Service
@Transactional
public class NoteService {

    private static final String NOTE = ClinicalNote.class.getSimpleName();
    private static final String SIGNED = "The note is signed and can no longer change; add an addendum instead";

    private final ClinicalNoteRepository notes;
    private final NoteAddendumRepository addenda;
    private final VisitService visits;
    private final PatientService patients;
    private final AccessPolicy accessPolicy;
    private final StaffService staff;
    private final CodeCheck codeCheck;
    private final AuditService audit;
    private final Clock clock;

    public NoteService(
            ClinicalNoteRepository notes,
            NoteAddendumRepository addenda,
            VisitService visits,
            PatientService patients,
            AccessPolicy accessPolicy,
            StaffService staff,
            CodeCheck codeCheck,
            AuditService audit,
            Clock clock) {
        this.notes = notes;
        this.addenda = addenda;
        this.visits = visits;
        this.patients = patients;
        this.accessPolicy = accessPolicy;
        this.staff = staff;
        this.codeCheck = codeCheck;
        this.audit = audit;
        this.clock = clock;
    }

    /** The visit's note for the care team; the read is access-logged. 404 if none yet. */
    public NoteWithAddenda get(UUID visitId, VisitService.Actor actor) {
        accessPolicy.requireVisitAccess(actor, visitId, ClinicalData.NOTE, true);
        ClinicalNote note =
                notes.findByVisitId(visitId).orElseThrow(() -> new NotFoundException("No note for this visit yet"));
        return withAddenda(note);
    }

    /** Creates the draft on the first save, then replaces its text on each later save. */
    public NoteWithAddenda saveDraft(UUID visitId, SaveNoteRequest request, VisitService.Actor actor) {
        ClinicalNote note = openDraft(visitId, request.version(), actor);
        note.write(request.sections(), request.usedAi());
        return withAddenda(notes.saveAndFlush(note));
    }

    /**
     * Adds accepted AI claims to the draft, each as a new line at the end of its section,
     * and marks the note AI-assisted. The same version rule as a save applies, so the
     * editor saves its own typing first.
     *
     * @param lines section name ({@link NoteSections#NAMES}) and the text to add, in order
     */
    public NoteWithAddenda addAiLines(
            UUID visitId, @Nullable Long version, List<Map.Entry<String, String>> lines, VisitService.Actor actor) {
        ClinicalNote note = openDraft(visitId, version, actor);
        NoteSections sections = note.sections();
        for (Map.Entry<String, String> line : lines) {
            sections = sections.withLine(line.getKey(), line.getValue());
        }
        note.write(sections, true);
        return withAddenda(notes.saveAndFlush(note));
    }

    /** The visit's draft, locked, or a new one: refused if signed or if the version is not the current one. */
    private ClinicalNote openDraft(UUID visitId, @Nullable Long version, VisitService.Actor actor) {
        requireDoctor(actor);
        accessPolicy.requireVisitAccess(actor, visitId, ClinicalData.NOTE, false);
        Visit visit = visits.get(visitId);

        ClinicalNote note = notes.lockByVisitId(visitId).orElse(null);
        if (note == null) {
            if (version != null) {
                throw stale();
            }
            return new ClinicalNote(visitId, visit.getPatientId(), actor.staffId());
        }
        if (note.isSigned()) {
            throw new ConflictException(SIGNED);
        }
        requireVersion(note, version);
        return note;
    }

    public NoteWithAddenda sign(UUID visitId, long version, @Nullable String code, VisitService.Actor actor) {
        requireDoctor(actor);
        accessPolicy.requireVisitAccess(actor, visitId, ClinicalData.NOTE, false);
        ClinicalNote note =
                notes.lockByVisitId(visitId).orElseThrow(() -> new NotFoundException("No note for this visit yet"));
        if (note.isSigned()) {
            throw new ConflictException(SIGNED);
        }
        requireVersion(note, version);
        NoteSections sections = note.sections();
        if (!StringUtils.hasText(sections.assessment()) || !StringUtils.hasText(sections.plan())) {
            throw new BusinessRuleException("Write the assessment and the plan before signing");
        }
        Staff doctor = staff.findById(actor.staffId()).orElseThrow(() -> new ForbiddenException("Unknown staff"));
        codeCheck.require(doctor, code);

        note.sign(actor.staffId(), clock.instant());
        ClinicalNote signed = notes.saveAndFlush(note);
        audit.record(
                AuditAction.NOTE_SIGNED,
                NOTE,
                signed.getId(),
                signed.getPatientId(),
                visitId,
                Map.of("aiAssisted", signed.isAiAssisted()));
        return withAddenda(signed);
    }

    public NoteWithAddenda addAddendum(UUID visitId, String text, VisitService.Actor actor) {
        requireDoctor(actor);
        accessPolicy.requireVisitAccess(actor, visitId, ClinicalData.NOTE, false);
        ClinicalNote note =
                notes.lockByVisitId(visitId).orElseThrow(() -> new NotFoundException("No note for this visit yet"));
        if (!note.isSigned()) {
            throw new ConflictException("Addenda are for signed notes; edit the draft instead");
        }
        NoteAddendum addendum = addenda.save(new NoteAddendum(note.getId(), actor.staffId(), text.strip()));
        audit.record(
                AuditAction.NOTE_ADDENDUM_ADDED,
                NoteAddendum.class.getSimpleName(),
                addendum.getId(),
                note.getPatientId(),
                visitId,
                Map.of("noteId", note.getId()));
        return withAddenda(note);
    }

    /**
     * The patient's signed notes, newest first, for the doctor looking after a current visit
     * of the patient. Every note returned is access-logged.
     */
    public List<NoteWithAddenda> history(UUID patientId, VisitService.Actor actor) {
        patients.get(patientId); // 404 for an unknown patient, before any access decision
        accessPolicy.requirePastRecordsAccess(actor, patientId, ClinicalData.PAST_SIGNED_NOTE);
        return notes.findByPatientIdAndStatusOrderBySignedAtDesc(patientId, NoteStatus.SIGNED).stream()
                .map(note -> {
                    accessPolicy.requireVisitAccess(actor, note.getVisitId(), ClinicalData.PAST_SIGNED_NOTE, true);
                    return withAddenda(note);
                })
                .toList();
    }

    private NoteWithAddenda withAddenda(ClinicalNote note) {
        return new NoteWithAddenda(note, addenda.findByNoteIdOrderByCreatedAtAsc(note.getId()));
    }

    private static void requireDoctor(VisitService.Actor actor) {
        if (actor.role() != StaffRole.DOCTOR) {
            throw new ForbiddenException("Notes are written by the doctor");
        }
    }

    private static void requireVersion(ClinicalNote note, @Nullable Long version) {
        if (version == null || version != note.getVersion()) {
            throw stale();
        }
    }

    private static OptimisticLockingFailureException stale() {
        return new OptimisticLockingFailureException("The note was saved elsewhere since it was loaded");
    }
}
