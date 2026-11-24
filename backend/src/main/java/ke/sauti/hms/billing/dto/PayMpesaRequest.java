package ke.sauti.hms.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Sending an M-Pesa prompt. The phone number is used for the prompt and never stored or logged.
 *
 * @param phone 07XXXXXXXX, 01XXXXXXXX or the same with 254 in front
 * @param idempotencyKey made once by the screen, so a double click sends one prompt
 */
public record PayMpesaRequest(
        @NotBlank @Pattern(regexp = "(?:254|0)[17][0-9]{8}", message = "Enter a Kenyan phone number")
        String phone,

        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9-]+")
        String idempotencyKey) {

    /** The number in the 2547XXXXXXXX form M-Pesa expects. */
    public String internationalPhone() {
        return phone.startsWith("0") ? "254" + phone.substring(1) : phone;
    }

    /** Keeps the phone number out of anything that prints this request. */
    @Override
    public String toString() {
        return "PayMpesaRequest[phone=hidden]";
    }
}
