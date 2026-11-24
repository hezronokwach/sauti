package ke.sauti.hms.patient.dto;

import java.time.LocalDate;
import java.util.UUID;
import ke.sauti.hms.patient.Patient;
import ke.sauti.hms.patient.PatientNumbers;
import ke.sauti.hms.patient.Sex;

/**
 * A patient in a search result or duplicate warning: enough to recognise the person,
 * no identifiers.
 *
 * @param id the patient id
 * @param patientNumber as shown, for example SH-000123
 * @param fullName the patient's name
 * @param sex as recorded
 * @param dateOfBirth date of birth
 * @param ageYears whole years today
 */
public record PatientSummaryResponse(
        UUID id, String patientNumber, String fullName, Sex sex, LocalDate dateOfBirth, int ageYears) {

    public static PatientSummaryResponse of(Patient patient, LocalDate today) {
        return new PatientSummaryResponse(
                patient.getId(),
                PatientNumbers.format(patient.getPatientNumber()),
                patient.getFullName(),
                patient.getSex(),
                patient.getDateOfBirth(),
                patient.ageOn(today));
    }
}
