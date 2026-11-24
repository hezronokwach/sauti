package ke.sauti.hms.staff;

/** Whether the account may log in. Disabling ends the person's sessions at once (issue 14). */
public enum StaffStatus {
    ACTIVE,
    DISABLED
}
