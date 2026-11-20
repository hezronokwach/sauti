package ke.sauti.hms.capture;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import ke.sauti.hms.common.persistence.BaseEntity;

/**
 * One uploaded piece of a recording (about 30 seconds). The audio itself is encrypted in
 * object storage under {@code storageKey}; this row records where, how long, and its hash.
 * Uploads arrive in issue 23.
 */
@Entity
@Table(name = "audio_piece")
public class AudioPiece extends BaseEntity {

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "sequence_no", nullable = false, updatable = false)
    private int sequenceNo;

    @Column(name = "storage_key", nullable = false, length = 200, updatable = false)
    private String storageKey;

    @Column(name = "duration_ms", nullable = false, updatable = false)
    private int durationMs;

    @Column(name = "offset_ms", nullable = false, updatable = false)
    private int offsetMs;

    @Column(nullable = false, length = 64, updatable = false)
    private String sha256;

    @Column(name = "mime_type", nullable = false, length = 50, updatable = false)
    private String mimeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AudioPieceStatus status = AudioPieceStatus.STORED;

    /** The reception field this piece was recorded for, if any; its transcript drafts that field. */
    @Enumerated(EnumType.STRING)
    @Column(name = "field_tag", length = 30, updatable = false)
    private DraftField fieldTag;

    protected AudioPiece() {
        // for JPA
    }

    AudioPiece(
            UUID sessionId,
            int sequenceNo,
            String storageKey,
            int durationMs,
            int offsetMs,
            String sha256,
            String mimeType) {
        this.mimeType = mimeType;
        this.sessionId = sessionId;
        this.sequenceNo = sequenceNo;
        this.storageKey = storageKey;
        this.durationMs = durationMs;
        this.offsetMs = offsetMs;
        this.sha256 = sha256;
    }

    void markStatus(AudioPieceStatus next) {
        this.status = next;
    }

    void tagField(DraftField field) {
        this.fieldTag = field;
    }

    public DraftField getFieldTag() {
        return fieldTag;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public int getDurationMs() {
        return durationMs;
    }

    public int getOffsetMs() {
        return offsetMs;
    }

    public String getSha256() {
        return sha256;
    }

    public String getMimeType() {
        return mimeType;
    }

    public AudioPieceStatus getStatus() {
        return status;
    }
}
