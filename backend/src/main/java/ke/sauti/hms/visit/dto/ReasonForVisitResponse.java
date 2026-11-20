package ke.sauti.hms.visit.dto;

import org.jspecify.annotations.Nullable;

/**
 * What brought the patient in, as reception recorded it.
 *
 * @param reasonForVisit the patient's words, or null if none were recorded
 * @param confirmed whether the triage nurse confirmed it with the patient
 */
public record ReasonForVisitResponse(@Nullable String reasonForVisit, boolean confirmed) {}
