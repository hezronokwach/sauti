package ke.sauti.hms.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import ke.sauti.hms.capture.DraftField;
import ke.sauti.hms.common.crypto.EncryptedStringConverter;
import ke.sauti.hms.common.persistence.BaseEntity;

/**
 * A field value drafted from speech, waiting for a person. Kept apart from the patient
 * record: confirming it means the person saves the value through the normal form.
 */
@Entity
@Table(name = "field_draft")
public class FieldDraft extends BaseEntity {

    /** Waiting, used (the person saved it through the form), or set aside. */
    public enum Status {
        PENDING,
        CONFIRMED,
        DISMISSED
    }

    @Column(name = "visit_id", nullable = false, updatable = false)
    private UUID visitId;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "piece_id", nullable = false, updatable = false)
    private UUID pieceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private DraftField field;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, updatable = false)
    private String value;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, updatable = false)
    private String quote;

    @Column(name = "start_ms", nullable = false, updatable = false)
    private int startMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "decided_by")
    private UUID decidedBy;

    protected FieldDraft() {
        // for JPA
    }

    FieldDraft(UUID visitId, UUID sessionId, UUID pieceId, DraftField field, String value, String quote, int startMs) {
        this.visitId = visitId;
        this.sessionId = sessionId;
        this.pieceId = pieceId;
        this.field = field;
        this.value = value;
        this.quote = quote;
        this.startMs = startMs;
        this.status = Status.PENDING;
    }

    void decide(Status decision, UUID staffId) {
        this.status = decision;
        this.decidedBy = staffId;
    }

    public UUID getVisitId() {
        return visitId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getPieceId() {
        return pieceId;
    }

    public DraftField getField() {
        return field;
    }

    public String getValue() {
        return value;
    }

    public String getQuote() {
        return quote;
    }

    public int getStartMs() {
        return startMs;
    }

    public Status getStatus() {
        return status;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }
}
