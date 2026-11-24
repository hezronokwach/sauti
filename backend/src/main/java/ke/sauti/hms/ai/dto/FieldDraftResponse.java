package ke.sauti.hms.ai.dto;

import java.util.UUID;
import ke.sauti.hms.ai.FieldDraft;
import ke.sauti.hms.capture.DraftField;

/**
 * A field value drafted from speech, waiting to be confirmed or set aside.
 *
 * @param id the draft
 * @param field which field it is for
 * @param value the proposed value
 * @param quote the transcript words it comes from
 * @param sessionId the recording, to play the words
 * @param startMs where the words start in the recording
 * @param status PENDING, CONFIRMED or DISMISSED
 */
public record FieldDraftResponse(
        UUID id, DraftField field, String value, String quote, UUID sessionId, int startMs, String status) {

    public static FieldDraftResponse of(FieldDraft draft) {
        return new FieldDraftResponse(
                draft.getId(),
                draft.getField(),
                draft.getValue(),
                draft.getQuote(),
                draft.getSessionId(),
                draft.getStartMs(),
                draft.getStatus().name());
    }
}
