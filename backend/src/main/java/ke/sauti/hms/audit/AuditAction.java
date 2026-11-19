package ke.sauti.hms.audit;

/** Everything that is written to the audit log. Each feature adds its own actions. */
public enum AuditAction {
    LOGIN_OK,
    LOGIN_FAILED,
    LOGOUT,
    STAFF_CREATED,
    STAFF_DISABLED,
    ACCESS_DENIED
}
