package ke.sauti.hms.patient.dto;

import java.util.List;
import ke.sauti.hms.common.error.ApiError;

/**
 * The 409 for a registration that matches existing patients: the usual error fields plus
 * the matches, so reception can pick the existing patient instead of creating a second
 * record. Has every field of {@link ApiError}, so clients read it as one.
 *
 * @param code always DUPLICATE_PATIENT
 * @param message safe to show
 * @param fieldErrors always empty
 * @param requestId to quote in support
 * @param matches the patients with the same phone number or national ID
 * @param confirmable true when only the phone matched, so registering a different person
 *     (a shared family phone) is allowed with confirmNewPatient
 */
public record DuplicatePatientResponse(
        String code,
        String message,
        List<ApiError.FieldError> fieldErrors,
        String requestId,
        List<PatientSummaryResponse> matches,
        boolean confirmable) {}
