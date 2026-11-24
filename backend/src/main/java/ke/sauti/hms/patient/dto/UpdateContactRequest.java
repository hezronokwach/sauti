package ke.sauti.hms.patient.dto;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Where the patient lives and who to contact, typed or confirmed from a voice draft. A blank
 * value clears the field.
 *
 * @param residence village, estate, town or county
 * @param nextOfKinName the person to contact
 */
public record UpdateContactRequest(
        @Nullable @Size(max = 200) String residence,
        @Nullable @Size(max = 200) String nextOfKinName) {}
