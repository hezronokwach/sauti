package ke.sauti.hms.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ke.sauti.hms.staff.Staff;

/**
 * Replacing the password, required after a one-time password.
 *
 * @param currentPassword the password used to log in
 * @param newPassword the chosen password, 12 to 128 characters
 */
public record ChangePasswordRequest(
        @NotBlank @Size(max = Staff.MAX_PASSWORD_LENGTH) String currentPassword,

        @NotBlank @Size(min = Staff.MIN_PASSWORD_LENGTH, max = Staff.MAX_PASSWORD_LENGTH)
        String newPassword) {}
