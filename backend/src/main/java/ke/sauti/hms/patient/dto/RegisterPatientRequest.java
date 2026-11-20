package ke.sauti.hms.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import ke.sauti.hms.patient.PayerType;
import ke.sauti.hms.patient.PreferredLanguage;
import ke.sauti.hms.patient.Sex;
import ke.sauti.hms.patient.ValidKenyanPhone;
import org.jspecify.annotations.Nullable;

/**
 * A new patient, entered at reception.
 *
 * @param fullName the patient's name as they give it
 * @param dateOfBirth not in the future
 * @param sex as recorded
 * @param phone Kenyan mobile number, optional (children and some elderly patients have none)
 * @param nationalId Kenyan national ID number, optional
 * @param shaNumber Social Health Authority number; required when the payer is SHA
 * @param residence where the patient lives
 * @param nextOfKinName who to contact
 * @param nextOfKinPhone their Kenyan mobile number
 * @param preferredLanguage for spoken instructions; Swahili if not given
 * @param payerType who pays by default; cash if not given
 * @param confirmNewPatient true after reception has seen the patients sharing this phone
 *     number and confirmed this is a different person (a shared family phone)
 */
public record RegisterPatientRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotNull @PastOrPresent LocalDate dateOfBirth,
        @NotNull Sex sex,
        @Nullable @ValidKenyanPhone String phone,

        @Nullable @Pattern(regexp = "\\s*\\d{6,9}\\s*", message = "must be 6 to 9 digits")
        String nationalId,

        @Nullable @Size(max = 30) String shaNumber,
        @Nullable @Size(max = 200) String residence,
        @Nullable @Size(max = 200) String nextOfKinName,
        @Nullable @ValidKenyanPhone String nextOfKinPhone,
        @Nullable PreferredLanguage preferredLanguage,
        @Nullable PayerType payerType,
        @Nullable Boolean confirmNewPatient) {

    /** Whether reception confirmed a different person on a shared phone; not sent means no. */
    public boolean confirmedNewPatient() {
        return Boolean.TRUE.equals(confirmNewPatient);
    }
}
