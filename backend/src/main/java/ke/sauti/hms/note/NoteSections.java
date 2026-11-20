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
        @Nullable String plan) {

    /** The section names AI claims use, matching the summary prompt. */
    public static final java.util.Set<String> NAMES =
            java.util.Set.of("complaint", "history", "examination", "assessment", "plan");

    /** These sections with a line added at the end of one of them. */
    public NoteSections withLine(String section, String line) {
        return switch (section) {
            case "complaint" ->
                new NoteSections(add(presentingComplaint, line), history, examination, assessment, plan);
            case "history" -> new NoteSections(presentingComplaint, add(history, line), examination, assessment, plan);
            case "examination" ->
                new NoteSections(presentingComplaint, history, add(examination, line), assessment, plan);
            case "assessment" ->
                new NoteSections(presentingComplaint, history, examination, add(assessment, line), plan);
            case "plan" -> new NoteSections(presentingComplaint, history, examination, assessment, add(plan, line));
            default -> throw new IllegalArgumentException("Unknown note section " + section);
        };
    }

    private static String add(@Nullable String existing, String line) {
        return existing == null || existing.isBlank() ? line : existing.stripTrailing() + "\n" + line;
    }
}
