package ke.sauti.hms.capture;

import java.util.List;

/**
 * Turns audio into timed text. The fake ({@link FakeSpeechToText}) is used until a real
 * provider is configured; the Groq Whisper adapter arrives in issue 27.
 *
 * <p>An adapter throws {@link ke.sauti.hms.jobs.JobFailure.RetryAfter} when the provider
 * asks to wait, {@link ke.sauti.hms.jobs.JobFailure.Permanent} when the audio itself is
 * refused, and anything else for a failure worth retrying.
 */
public interface SpeechToText {

    /**
     * Transcribes one piece of audio.
     *
     * @param audio the decrypted audio bytes
     * @param mimeType for example audio/webm
     * @param languageHint "sw", "en", or null to let the provider decide
     * @param prompt words to bias recognition: the medicine glossary and the last words heard
     * @return segments with times relative to the start of this piece
     */
    List<Segment> transcribe(byte[] audio, String mimeType, String languageHint, String prompt) throws Exception;

    /**
     * One recognised stretch of speech, with Whisper's own confidence numbers.
     *
     * @param text what was heard
     * @param startMs start, from the beginning of the piece
     * @param endMs end, from the beginning of the piece
     * @param avgLogprob average log probability of the tokens (closer to 0 is surer)
     * @param noSpeechProb probability the stretch was silence
     * @param compressionRatio high values mean repetitive text, a sign of a hallucination loop
     */
    record Segment(
            String text, int startMs, int endMs, double avgLogprob, double noSpeechProb, double compressionRatio) {}
}
