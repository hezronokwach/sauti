package ke.sauti.hms.prereg.dto;

import java.time.LocalDate;
import java.util.UUID;
import ke.sauti.hms.patient.PreferredLanguage;
import ke.sauti.hms.patient.Sex;
import ke.sauti.hms.prereg.PreRegistration;
import org.jspecify.annotations.Nullable;

/**
 * A pre-registration for reception to check with the patient. Every field was typed by the
 * patient and is unverified.
 *
 * @param id the entry
 * @param ticketId its P ticket
 * @param fullName as typed
 * @param dateOfBirth as typed
 * @param sex as chosen
 * @param phone normalised
 * @param reasonForVisit as typed, if any
 * @param preferredLanguage as chosen
 */
public record PreRegistrationResponse(
        UUID id,
        UUID ticketId,
        String fullName,
        LocalDate dateOfBirth,
        Sex sex,
        String phone,
        @Nullable String reasonForVisit,
        PreferredLanguage preferredLanguage) {

    public static PreRegistrationResponse of(PreRegistration entry) {
        PreRegistration.Details d = entry.details();
        return new PreRegistrationResponse(
                entry.getId(),
                entry.getTicketId(),
                d.fullName(),
                d.dateOfBirth(),
                d.sex(),
                d.phone(),
                d.reasonForVisit(),
                d.preferredLanguage());
    }
}
