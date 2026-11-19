package ke.sauti.hms.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The code step of login.
 *
 * @param code the 6-digit code from the authenticator app
 */
public record LoginCodeRequest(@NotBlank @Size(max = 10) String code) {}
