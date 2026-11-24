package ke.sauti.hms.patient;

import java.util.UUID;

/**
 * Published inside the registration transaction, so listeners can queue follow-up work
 * (such as the eligibility check) that commits or rolls back with the patient.
 *
 * @param patientId the new patient
 * @param hasNationalId whether a national ID was recorded
 */
public record PatientRegisteredEvent(UUID patientId, boolean hasNationalId) {}
