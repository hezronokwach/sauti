package ke.sauti.hms.ai;

import java.util.List;

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

    /** The model name recorded with every request. */
    String model();

    /** The prompt version recorded with every request. */
    String promptVersion();

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
