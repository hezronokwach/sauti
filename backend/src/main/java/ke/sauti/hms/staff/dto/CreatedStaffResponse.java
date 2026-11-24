package ke.sauti.hms.staff.dto;

/**
 * The new account and its one-time password. This is the only time the password is
 * shown; the server keeps only its hash. The admin passes it on, and the person changes
 * it at first login.
 *
 * @param staff the new account
 * @param oneTimePassword the password for the first login
 */
public record CreatedStaffResponse(StaffSummaryResponse staff, String oneTimePassword) {}
