package ke.sauti.hms.prereg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import ke.sauti.hms.patient.PreferredLanguage;
import ke.sauti.hms.patient.Sex;
import org.jspecify.annotations.Nullable;

/**
 * What a patient types on the public page. No national ID: that is checked at the desk.
 *
 * @param fullName the patient's name
 * @param dateOfBirth their date of birth
 * @param sex FEMALE, MALE or INTERSEX
 * @param phone a Kenyan mobile number
 * @param reasonForVisit why they are coming, optional
 * @param preferredLanguage SWAHILI (default) or ENGLISH
 */
public record PreRegisterRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotNull LocalDate dateOfBirth,
        @NotNull Sex sex,
        @NotBlank @Size(max = 20) String phone,
        @Nullable @Size(max = 500) String reasonForVisit,
        @Nullable PreferredLanguage preferredLanguage) {}
