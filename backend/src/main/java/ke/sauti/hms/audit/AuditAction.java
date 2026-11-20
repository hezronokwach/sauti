package ke.sauti.hms.audit;

/** Everything that is written to the audit log. Each feature adds its own actions. */
public enum AuditAction {
    LOGIN_OK,
    LOGIN_FAILED,
    LOGOUT,
    PASSWORD_CHANGED,
    STAFF_CREATED,
    STAFF_DISABLED,
    ACCESS_DENIED,
    PATIENT_SEARCH,
    PATIENT_REGISTERED,
    ALLERGY_RECORDED,
    ALLERGY_ENTERED_IN_ERROR,
    VISIT_OPENED,
    VISIT_STATUS_CHANGED
}
