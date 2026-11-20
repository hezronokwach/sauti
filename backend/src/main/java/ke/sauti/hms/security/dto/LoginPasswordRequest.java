package ke.sauti.hms.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The password step of login.
 *
 * @param email the login email, matched regardless of case
 * @param password the password as typed
 */
public record LoginPasswordRequest(
        @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(max = 128) String password) {}
