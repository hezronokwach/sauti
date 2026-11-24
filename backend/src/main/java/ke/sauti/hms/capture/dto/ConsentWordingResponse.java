package ke.sauti.hms.capture.dto;

import ke.sauti.hms.patient.PreferredLanguage;

/**
 * The consent wording to read to the patient.
 *
 * @param version the version label recorded with the decision, for example v1-sw
 * @param language the language of the text
 * @param text the wording
 * @param demonstration true while the wording is demonstration wording, not approved for real use
 */
public record ConsentWordingResponse(String version, PreferredLanguage language, String text, boolean demonstration) {}
