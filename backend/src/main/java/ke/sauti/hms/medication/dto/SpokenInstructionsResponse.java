package ke.sauti.hms.medication.dto;

import java.util.List;
import ke.sauti.hms.patient.PreferredLanguage;

/**
 * A signed prescription as words for the patient.
 *
 * @param language the language of the text
 * @param version the wording version
 * @param serverAudio true when the server has a voice; otherwise the browser speaks the text
 * @param lines one instruction per medicine, in prescription order
 */
public record SpokenInstructionsResponse(
        PreferredLanguage language, String version, boolean serverAudio, List<SpokenLineResponse> lines) {}
