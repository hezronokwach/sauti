package ke.sauti.hms.patient;

import java.io.Serial;
import java.util.List;
import ke.sauti.hms.common.error.ApiException;
import ke.sauti.hms.common.error.ErrorCode;
import ke.sauti.hms.patient.dto.PatientSummaryResponse;

/**
 * A registration matching existing patients by phone number or national ID. Carries the
 * matches so reception can choose the existing record.
 */
public class DuplicatePatientException extends ApiException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient List<PatientSummaryResponse> matches;
    private final boolean confirmable;

    DuplicatePatientException(List<PatientSummaryResponse> matches, boolean confirmable) {
        super(ErrorCode.DUPLICATE_PATIENT, ErrorCode.DUPLICATE_PATIENT.defaultMessage());
        this.matches = List.copyOf(matches);
        this.confirmable = confirmable;
    }

    public List<PatientSummaryResponse> getMatches() {
        return matches;
    }

    /** True when only the phone matched, so a different person may still be registered. */
    public boolean isConfirmable() {
        return confirmable;
    }
}
