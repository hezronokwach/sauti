package ke.sauti.hms.patient.dto;

import java.time.LocalDate;
import java.util.UUID;
import ke.sauti.hms.patient.Patient;
import ke.sauti.hms.patient.PatientNumbers;
import ke.sauti.hms.patient.PayerType;
import ke.sauti.hms.patient.PreferredLanguage;
import ke.sauti.hms.patient.Sex;
import org.jspecify.annotations.Nullable;

/**
 * A patient's registration details, decrypted, for the people who need them.
 *
 * @param id the patient id
 * @param patientNumber as shown, for example SH-000123
 * @param fullName the patient's name
 * @param dateOfBirth date of birth
 * @param ageYears whole years today
 * @param sex as recorded
 * @param phone normalised, for example 254712345678
 * @param nationalId national ID number
 * @param shaNumber Social Health Authority number
 * @param residence where the patient lives
 * @param nextOfKinName who to contact
 * @param nextOfKinPhone their number, normalised
 * @param preferredLanguage for spoken instructions
 * @param payerType who pays by default
 */
public record PatientDetailsResponse(
        UUID id,
        String patientNumber,
        String fullName,
        LocalDate dateOfBirth,
        int ageYears,
        Sex sex,
        @Nullable String phone,
        @Nullable String nationalId,
        @Nullable String shaNumber,
        @Nullable String residence,
        @Nullable String nextOfKinName,
        @Nullable String nextOfKinPhone,
        PreferredLanguage preferredLanguage,
        PayerType payerType) {

    public static PatientDetailsResponse of(Patient patient, LocalDate today) {
        return new PatientDetailsResponse(
                patient.getId(),
                PatientNumbers.format(patient.getPatientNumber()),
                patient.getFullName(),
                patient.getDateOfBirth(),
                patient.ageOn(today),
                patient.getSex(),
                patient.getPhone(),
                patient.getNationalId(),
                patient.getShaNumber(),
                patient.getResidence(),
                patient.getNextOfKinName(),
                patient.getNextOfKinPhone(),
                patient.getPreferredLanguage(),
                patient.getPayerType());
    }
}
