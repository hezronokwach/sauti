package ke.sauti.hms.ai;

import java.util.List;
import java.util.Optional;
import ke.sauti.hms.capture.DraftField;

/**
 * The clinical AI, behind a port so development and tests run on a fake and the demo on
 * Groq. Implementations throw on any failure; the caller retries once and then reports the
 * summary as unavailable.
 */
public interface ClinicalAi {

    /**
     * Drafts summary claims from a consultation transcript. Every claim must carry the exact
     * words of the transcript it rests on; the server checks them afterwards.
     *
     * @param transcript the transcript, one line per segment
     * @return the claims, possibly empty
     */
    List<DraftClaim> summarise(String transcript);

    /**
     * Drafts one reception field from what was said while reception was on it. The quote
     * must be words from the transcript; the server checks it.
     *
     * @return the proposal, or empty when the transcript does not say it
     */
    Optional<FieldProposal> draftField(DraftField field, String transcript);

    /** The model name recorded with every request. */
    String model();

    /** The prompt version recorded with every summary. */
    String promptVersion();

    /** The prompt version recorded with every field draft. */
    String fieldPromptVersion();

    /**
     * A proposed field value.
     *
     * @param value what would go in the field
     * @param quote the transcript words it comes from
     */
    record FieldProposal(String value, String quote) {}

    /**
     * One claim as the model wrote it.
     *
     * @param section which part of the note it belongs to (complaint, history, examination,
     *     assessment or plan)
     * @param text the claim, in English
     * @param quote the transcript words it rests on, exactly as transcribed
     */
    record DraftClaim(String section, String text, String quote) {}
}
