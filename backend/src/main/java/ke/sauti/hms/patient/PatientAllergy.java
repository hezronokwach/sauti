package ke.sauti.hms.patient;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import ke.sauti.hms.common.persistence.BaseEntity;

/**
 * One allergy, or the record that the patient has no known allergies. Entries are never
 * edited or deleted: a wrong one is marked entered in error, with a reason, and stays
 * visible in the history.
 */
@Entity
@Table(name = "patient_allergy")
public class PatientAllergy extends BaseEntity {

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private AllergyKind kind;

    @Column(length = 100, updatable = false)
    private String substance;

    @Column(name = "substance_group", length = 100, updatable = false)
    private String substanceGroup;

    @Column(length = 200, updatable = false)
    private String reaction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AllergyStatus status = AllergyStatus.ACTIVE;

    @Column(name = "recorded_by", nullable = false, updatable = false)
    private UUID recordedBy;

    @Column(name = "entered_in_error_reason", length = 200)
    private String enteredInErrorReason;

    @Column(name = "entered_in_error_by")
    private UUID enteredInErrorBy;

    protected PatientAllergy() {
        // for JPA
    }

    private PatientAllergy(UUID patientId, AllergyKind kind, UUID recordedBy) {
        this.patientId = Objects.requireNonNull(patientId, "patientId");
        this.kind = kind;
        this.recordedBy = Objects.requireNonNull(recordedBy, "recordedBy");
    }

    static PatientAllergy allergy(
            UUID patientId, String substance, String substanceGroup, String reaction, UUID recordedBy) {
        PatientAllergy allergy = new PatientAllergy(patientId, AllergyKind.ALLERGY, recordedBy);
        allergy.substance = Objects.requireNonNull(substance, "substance").trim();
        allergy.substanceGroup = substanceGroup;
        allergy.reaction = reaction;
        return allergy;
    }

    static PatientAllergy noKnownAllergies(UUID patientId, UUID recordedBy) {
        return new PatientAllergy(patientId, AllergyKind.NO_KNOWN_ALLERGIES, recordedBy);
    }

    void markEnteredInError(String reason, UUID by) {
        this.status = AllergyStatus.ENTERED_IN_ERROR;
        this.enteredInErrorReason = Objects.requireNonNull(reason, "reason").trim();
        this.enteredInErrorBy = Objects.requireNonNull(by, "by");
    }

    public boolean isActive() {
        return status == AllergyStatus.ACTIVE;
    }

    /** Whether this active allergy is to the same substance, ignoring case and spacing. */
    boolean isSameSubstance(String other) {
        return kind == AllergyKind.ALLERGY
                && substance.trim().toLowerCase(Locale.ROOT).equals(other.trim().toLowerCase(Locale.ROOT));
    }

    public UUID getPatientId() {
        return patientId;
    }

    public AllergyKind getKind() {
        return kind;
    }

    public String getSubstance() {
        return substance;
    }

    public String getSubstanceGroup() {
        return substanceGroup;
    }

    public String getReaction() {
        return reaction;
    }

    public AllergyStatus getStatus() {
        return status;
    }

    public UUID getRecordedBy() {
        return recordedBy;
    }

    public UUID getEnteredInErrorBy() {
        return enteredInErrorBy;
    }

    public String getEnteredInErrorReason() {
        return enteredInErrorReason;
    }
}
