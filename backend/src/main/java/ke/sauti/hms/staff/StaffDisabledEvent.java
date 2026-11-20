package ke.sauti.hms.staff;

import java.util.UUID;

/**
 * An account was disabled. Published inside the disabling transaction; listeners that
 * act on it (ending the person's sessions) wait until that transaction commits, so a
 * disable that rolls back logs nobody out.
 *
 * @param staffId the disabled account
 */
public record StaffDisabledEvent(UUID staffId) {}
