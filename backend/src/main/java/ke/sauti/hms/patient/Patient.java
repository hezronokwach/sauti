package ke.sauti.hms.patient;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;
import ke.sauti.hms.common.crypto.EncryptedStringConverter;
import ke.sauti.hms.common.persistence.BaseEntity;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * A registered patient. Identifiers are encrypted in the table; their keyed hashes
 * ({@code phoneHash}, {@code nationalIdHash}) are set alongside by {@link PatientService},
 * which normalises them first so lookups match.
 */
@Entity
@Table(name = "patient")
public class Patient extends BaseEntity {

    /** From a database sequence on insert; shown as SH-000123. */
    @Generated(event = EventType.INSERT)
    @Column(name = "patient_number", nullable = false, insertable = false, updatable = false)
    private Long patientNumber;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Sex sex;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "phone")
    private String phone;

    @Column(name = "phone_hash")
    private byte[] phoneHash;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "national_id")
    private String nationalId;

    @Column(name = "national_id_hash")
    private byte[] nationalIdHash;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "sha_number")
    private String shaNumber;

    @Column(length = 200)
    private String residence;

    @Column(name = "next_of_kin_name", length = 200)
    private String nextOfKinName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "next_of_kin_phone")
    private String nextOfKinPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_language", nullable = false, length = 10)
    private PreferredLanguage preferredLanguage;

    @Enumerated(EnumType.STRING)
    @Column(name = "payer_type", nullable = false, length = 10)
    private PayerType payerType;

    protected Patient() {
        // for JPA
    }

    Patient(String fullName, LocalDate dateOfBirth, Sex sex, PreferredLanguage language, PayerType payerType) {
        this.fullName = Objects.requireNonNull(fullName, "fullName").trim();
        this.dateOfBirth = Objects.requireNonNull(dateOfBirth, "dateOfBirth");
        this.sex = Objects.requireNonNull(sex, "sex");
        this.preferredLanguage = Objects.requireNonNull(language, "language");
        this.payerType = Objects.requireNonNull(payerType, "payerType");
    }

    /** Sets the normalised phone and its lookup hash together. */
    void setPhone(String normalisedPhone, byte[] hash) {
        this.phone = normalisedPhone;
        this.phoneHash = hash == null ? null : hash.clone();
    }

    /** Sets the national ID and its lookup hash together. */
    void setNationalId(String nationalId, byte[] hash) {
        this.nationalId = nationalId;
        this.nationalIdHash = hash == null ? null : hash.clone();
    }

    void setDetails(String shaNumber, String residence, String nextOfKinName, String nextOfKinPhone) {
        this.shaNumber = shaNumber;
        this.residence = residence;
        this.nextOfKinName = nextOfKinName;
        this.nextOfKinPhone = nextOfKinPhone;
    }

    /** Whole years on the given day. */
    public int ageOn(LocalDate day) {
        return Period.between(dateOfBirth, day).getYears();
    }

    public Long getPatientNumber() {
        return patientNumber;
    }

    public String getFullName() {
        return fullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public Sex getSex() {
        return sex;
    }

    public String getPhone() {
        return phone;
    }

    public String getNationalId() {
        return nationalId;
    }

    public String getShaNumber() {
        return shaNumber;
    }

    public String getResidence() {
        return residence;
    }

    public String getNextOfKinName() {
        return nextOfKinName;
    }

    public String getNextOfKinPhone() {
        return nextOfKinPhone;
    }

    public PreferredLanguage getPreferredLanguage() {
        return preferredLanguage;
    }

    public PayerType getPayerType() {
        return payerType;
    }
}
