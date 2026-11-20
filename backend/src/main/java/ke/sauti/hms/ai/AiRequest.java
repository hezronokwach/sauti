package ke.sauti.hms.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import ke.sauti.hms.common.crypto.EncryptedStringConverter;
import ke.sauti.hms.common.persistence.BaseEntity;

/** One call to the clinical AI. Never changed once written. */
@Entity
@Table(name = "ai_request")
public class AiRequest extends BaseEntity {

    static final String SUMMARY = "SUMMARY";
    static final String OK = "OK";
    static final String UNAVAILABLE = "UNAVAILABLE";

    @Column(name = "visit_id", nullable = false, updatable = false)
    private UUID visitId;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(nullable = false, updatable = false)
    private String kind;

    @Column(nullable = false, updatable = false)
    private String model;

    @Column(name = "prompt_version", nullable = false, updatable = false)
    private String promptVersion;

    @Column(nullable = false, updatable = false)
    private String status;

    /** The claims as the model returned them, as JSON. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(updatable = false)
    private String claims;

    /** The server's verdict on each claim, as JSON. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(updatable = false)
    private String checks;

    /** Why the summary was unavailable; never patient content. */
    @Column(updatable = false)
    private String error;

    @Column(name = "duration_ms", nullable = false, updatable = false)
    private int durationMs;

    protected AiRequest() {
        // for JPA
    }

    AiRequest(
            UUID visitId,
            UUID requestedBy,
            String model,
            String promptVersion,
            String status,
            String claims,
            String checks,
            String error,
            int durationMs) {
        this.visitId = visitId;
        this.requestedBy = requestedBy;
        this.kind = SUMMARY;
        this.model = model;
        this.promptVersion = promptVersion;
        this.status = status;
        this.claims = claims;
        this.checks = checks;
        this.error = error;
        this.durationMs = durationMs;
    }

    public UUID getVisitId() {
        return visitId;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public String getKind() {
        return kind;
    }

    public String getModel() {
        return model;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getStatus() {
        return status;
    }

    public String getClaims() {
        return claims;
    }

    public String getChecks() {
        return checks;
    }

    public String getError() {
        return error;
    }

    public int getDurationMs() {
        return durationMs;
    }
}
