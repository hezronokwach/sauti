package ke.sauti.hms.capture;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import ke.sauti.hms.common.crypto.EncryptedStringConverter;
import org.hibernate.annotations.UuidGenerator;

/**
 * One segment of transcribed speech, with its time span inside the session, its encrypted
 * text, a confidence grade, and Whisper's raw numbers for tuning. Written by the
 * transcription job (issue 23).
 */
@Entity
@Table(name = "transcript_segment")
public class TranscriptSegment {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "piece_id", nullable = false, updatable = false)
    private UUID pieceId;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "order_no", nullable = false, updatable = false)
    private int orderNo;

    @Column(name = "start_ms", nullable = false, updatable = false)
    private int startMs;

    @Column(name = "end_ms", nullable = false, updatable = false)
    private int endMs;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, updatable = false)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Confidence confidence;

    @Column(name = "avg_logprob", updatable = false)
    private Double avgLogprob;

    @Column(name = "no_speech_prob", updatable = false)
    private Double noSpeechProb;

    @Column(name = "compression_ratio", updatable = false)
    private Double compressionRatio;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TranscriptSegment() {
        // for JPA
    }

    TranscriptSegment(
            UUID pieceId,
            UUID sessionId,
            int orderNo,
            int startMs,
            int endMs,
            String text,
            Confidence confidence,
            Double avgLogprob,
            Double noSpeechProb,
            Double compressionRatio,
            Instant createdAt) {
        this.pieceId = pieceId;
        this.sessionId = sessionId;
        this.orderNo = orderNo;
        this.startMs = startMs;
        this.endMs = endMs;
        this.text = text;
        this.confidence = confidence;
        this.avgLogprob = avgLogprob;
        this.noSpeechProb = noSpeechProb;
        this.compressionRatio = compressionRatio;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPieceId() {
        return pieceId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public int getOrderNo() {
        return orderNo;
    }

    public int getStartMs() {
        return startMs;
    }

    public int getEndMs() {
        return endMs;
    }

    public String getText() {
        return text;
    }

    public Confidence getConfidence() {
        return confidence;
    }

    public Double getAvgLogprob() {
        return avgLogprob;
    }

    public Double getNoSpeechProb() {
        return noSpeechProb;
    }

    public Double getCompressionRatio() {
        return compressionRatio;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
