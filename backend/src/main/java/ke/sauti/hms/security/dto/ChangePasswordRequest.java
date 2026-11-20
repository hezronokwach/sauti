package ke.sauti.hms.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Replacing the password, required after a one-time password.
 *
 * @param currentPassword the password used to log in
 * @param newPassword the chosen password, 12 to 128 characters
 */
public record ChangePasswordRequest(
        @NotBlank @Size(max = 128) String currentPassword,
        @NotBlank @Size(min = 12, max = 128) String newPassword) {}
