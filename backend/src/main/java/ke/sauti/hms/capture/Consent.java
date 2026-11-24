package ke.sauti.hms.capture;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

/** One recorded consent decision. Never changed: a new decision is a new row. */
@Entity
@Immutable
@Table(name = "consent")
public class Consent {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ConsentPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConsentDecision decision;

    @Enumerated(EnumType.STRING)
    @Column(name = "given_by", nullable = false, length = 30)
    private ConsentGivenBy givenBy;

    @Column(name = "wording_version", nullable = false, length = 20)
    private String wordingVersion;

    @Column(name = "recorded_by", nullable = false)
    private UUID recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected Consent() {
        // for JPA
    }

    Consent(
            UUID visitId,
            ConsentPurpose purpose,
            ConsentDecision decision,
            ConsentGivenBy givenBy,
            String wordingVersion,
            UUID recordedBy,
            Instant recordedAt) {
        this.visitId = visitId;
        this.purpose = purpose;
        this.decision = decision;
        this.givenBy = givenBy;
        this.wordingVersion = wordingVersion;
        this.recordedBy = recordedBy;
        this.recordedAt = recordedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getVisitId() {
        return visitId;
    }

    public ConsentPurpose getPurpose() {
        return purpose;
    }

    public ConsentDecision getDecision() {
        return decision;
    }

    public ConsentGivenBy getGivenBy() {
        return givenBy;
    }

    public String getWordingVersion() {
        return wordingVersion;
    }

    public UUID getRecordedBy() {
        return recordedBy;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
