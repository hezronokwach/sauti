package ke.sauti.hms.note;

import org.jspecify.annotations.Nullable;

/**
 * The written parts of a note. Any may be empty while drafting; assessment and plan are
 * required to sign.
 *
 * @param presentingComplaint why the patient came, in their words
 * @param history history of the complaint and relevant past history
 * @param examination findings on examination
 * @param assessment the doctor's assessment or diagnosis
 * @param plan treatment, tests and follow-up
 */
public record NoteSections(
        @Nullable String presentingComplaint,
        @Nullable String history,
        @Nullable String examination,
        @Nullable String assessment,
        @Nullable String plan) {}
