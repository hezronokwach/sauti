package ke.sauti.hms.ai.dto;

import java.util.UUID;
import ke.sauti.hms.ai.CheckedClaim;
import ke.sauti.hms.ai.ClaimChecker;
import org.jspecify.annotations.Nullable;

/**
 * One summary claim with the server's checks.
 *
 * @param section complaint, history, examination, assessment or plan
 * @param text the claim
 * @param quote the transcript words it rests on
 * @param supported all three checks passed
 * @param quotePresent the quote is in the transcript
 * @param negationConsistent the claim and quote agree on negation
 * @param numbersMatch every number in the claim was said
 * @param checkAudio the quote comes from an unsure line: listen before accepting
 * @param startMs where to play from, if the quote was found
 * @param sessionId the recording to play, if the quote was found
 */
public record ClaimResponse(
        String section,
        String text,
        String quote,
        boolean supported,
        boolean quotePresent,
        boolean negationConsistent,
        boolean numbersMatch,
        boolean checkAudio,
        @Nullable Integer startMs,
        @Nullable UUID sessionId) {

    public static ClaimResponse of(CheckedClaim checked) {
        ClaimChecker.Verdict v = checked.verdict();
        return new ClaimResponse(
                checked.claim().section(),
                checked.claim().text(),
                checked.claim().quote(),
                v.supported(),
                v.quotePresent(),
                v.negationConsistent(),
                v.numbersMatch(),
                v.checkAudio(),
                v.startMs(),
                v.sessionId());
    }
}
