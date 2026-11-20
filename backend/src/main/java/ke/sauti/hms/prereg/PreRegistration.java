package ke.sauti.hms.prereg;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import ke.sauti.hms.common.crypto.EncryptedStringConverter;
import ke.sauti.hms.common.persistence.BaseEntity;
import ke.sauti.hms.patient.PreferredLanguage;
import ke.sauti.hms.patient.Sex;

/**
 * What a patient typed on the public page, unverified. Never changed, never linked to a
 * patient record by the server, and deleted at the end of its day.
 */
@Entity
@Table(name = "pre_registration")
public class PreRegistration extends BaseEntity {

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private UUID ticketId;

    @Column(name = "ticket_date", nullable = false, updatable = false)
    private LocalDate ticketDate;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "full_name", nullable = false, updatable = false)
    private String fullName;

    @Column(name = "date_of_birth", nullable = false, updatable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Sex sex;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, updatable = false)
    private String phone;

    @Column(name = "phone_hash", nullable = false, updatable = false)
    private byte[] phoneHash;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "reason_for_visit", updatable = false)
    private String reasonForVisit;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_language", nullable = false, updatable = false)
    private PreferredLanguage preferredLanguage;

    @Column(name = "ip_hash", nullable = false, updatable = false)
    private byte[] ipHash;

    protected PreRegistration() {
        // for JPA
    }

    PreRegistration(UUID ticketId, LocalDate ticketDate, Details details, byte[] phoneHash, byte[] ipHash) {
        this.ticketId = ticketId;
        this.ticketDate = ticketDate;
        this.fullName = details.fullName();
        this.dateOfBirth = details.dateOfBirth();
        this.sex = details.sex();
        this.phone = details.phone();
        this.phoneHash = phoneHash.clone();
        this.reasonForVisit = details.reasonForVisit();
        this.preferredLanguage = details.preferredLanguage();
        this.ipHash = ipHash.clone();
    }

    /**
     * What the patient entered.
     *
     * @param fullName as typed
     * @param dateOfBirth as typed
     * @param sex as chosen
     * @param phone normalised Kenyan mobile number
     * @param reasonForVisit as typed, if any
     * @param preferredLanguage as chosen
     */
    public record Details(
            String fullName,
            LocalDate dateOfBirth,
            Sex sex,
            String phone,
            String reasonForVisit,
            PreferredLanguage preferredLanguage) {}

    public Details details() {
        return new Details(fullName, dateOfBirth, sex, phone, reasonForVisit, preferredLanguage);
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public LocalDate getTicketDate() {
        return ticketDate;
    }

    public byte[] getPhoneHash() {
        return phoneHash.clone();
    }

    public byte[] getIpHash() {
        return ipHash.clone();
    }
}
