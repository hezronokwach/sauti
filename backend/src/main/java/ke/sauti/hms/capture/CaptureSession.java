package ke.sauti.hms.capture;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import ke.sauti.hms.common.persistence.BaseEntity;

/** One recording: started with consent, stopped by the user or by a withdrawal. */
@Entity
@Table(name = "capture_session")
public class CaptureSession extends BaseEntity {

    @Column(name = "visit_id", nullable = false, updatable = false)
    private UUID visitId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private ConsentPurpose purpose;

    @Column(name = "started_by", nullable = false, updatable = false)
    private UUID startedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CaptureStatus status = CaptureStatus.RECORDING;

    @Column(name = "language_hint", length = 10, updatable = false)
    private String languageHint;

    @Column(name = "expected_pieces")
    private Integer expectedPieces;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "stopped_at")
    private Instant stoppedAt;

    protected CaptureSession() {
        // for JPA
    }

    CaptureSession(UUID visitId, ConsentPurpose purpose, UUID startedBy, String languageHint, Instant startedAt) {
        this.visitId = visitId;
        this.purpose = purpose;
        this.startedBy = startedBy;
        this.languageHint = languageHint;
        this.startedAt = startedAt;
    }

    /** Stops recording; the expected piece count tells transcription when the session is complete. */
    void stop(Integer expectedPieces, Instant at) {
        if (status == CaptureStatus.RECORDING) {
            this.status = CaptureStatus.STOPPED;
            this.expectedPieces = expectedPieces;
            this.stoppedAt = at;
        }
    }

    public boolean isRecording() {
        return status == CaptureStatus.RECORDING;
    }

    public UUID getVisitId() {
        return visitId;
    }

    public ConsentPurpose getPurpose() {
        return purpose;
    }

    public UUID getStartedBy() {
        return startedBy;
    }

    public CaptureStatus getStatus() {
        return status;
    }

    public String getLanguageHint() {
        return languageHint;
    }

    public Integer getExpectedPieces() {
        return expectedPieces;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getStoppedAt() {
        return stoppedAt;
    }
}
