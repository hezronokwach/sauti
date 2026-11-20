package ke.sauti.hms.note;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import ke.sauti.hms.common.crypto.EncryptedStringConverter;
import ke.sauti.hms.common.persistence.BaseEntity;

/**
 * The doctor's note for one visit. The sections are encrypted at rest. Once signed, nothing
 * in it changes; corrections are addenda.
 */
@Entity
@Table(name = "clinical_note")
public class ClinicalNote extends BaseEntity {

    @Column(name = "visit_id", nullable = false, updatable = false)
    private UUID visitId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NoteStatus status;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "presenting_complaint")
    private String presentingComplaint;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "history")
    private String history;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "examination")
    private String examination;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "assessment")
    private String assessment;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "plan")
    private String plan;

    @Column(name = "ai_assisted", nullable = false)
    private boolean aiAssisted;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "signed_by")
    private UUID signedBy;

    protected ClinicalNote() {
        // for JPA
    }

    ClinicalNote(UUID visitId, UUID patientId, UUID authorId) {
        this.visitId = visitId;
        this.patientId = patientId;
        this.authorId = authorId;
        this.status = NoteStatus.DRAFT;
    }

    /** Replaces the draft's text. The service refuses this on a signed note first. */
    void write(NoteSections sections, boolean aiAssisted) {
        this.presentingComplaint = sections.presentingComplaint();
        this.history = sections.history();
        this.examination = sections.examination();
        this.assessment = sections.assessment();
        this.plan = sections.plan();
        // Once any AI help was used, the note stays marked as AI-assisted.
        this.aiAssisted = this.aiAssisted || aiAssisted;
    }

    void sign(UUID doctorId, Instant at) {
        this.status = NoteStatus.SIGNED;
        this.signedBy = doctorId;
        this.signedAt = at;
    }

    public NoteSections sections() {
        return new NoteSections(presentingComplaint, history, examination, assessment, plan);
    }

    public boolean isSigned() {
        return status == NoteStatus.SIGNED;
    }

    public UUID getVisitId() {
        return visitId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public NoteStatus getStatus() {
        return status;
    }

    public boolean isAiAssisted() {
        return aiAssisted;
    }

    public Instant getSignedAt() {
        return signedAt;
    }

    public UUID getSignedBy() {
        return signedBy;
    }
}
