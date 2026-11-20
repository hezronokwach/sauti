package ke.sauti.hms.note.dto;

import java.time.Instant;
import java.util.UUID;
import ke.sauti.hms.note.NoteAddendum;

/**
 * One addendum to a signed note.
 *
 * @param id the addendum id
 * @param authorId who wrote it
 * @param text what it says
 * @param createdAt when it was added
 */
public record AddendumResponse(UUID id, UUID authorId, String text, Instant createdAt) {

    public static AddendumResponse of(NoteAddendum addendum) {
        return new AddendumResponse(
                addendum.getId(), addendum.getAuthorId(), addendum.getBody(), addendum.getCreatedAt());
    }
}
