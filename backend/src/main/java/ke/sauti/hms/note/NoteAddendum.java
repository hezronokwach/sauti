package ke.sauti.hms.note;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import ke.sauti.hms.common.crypto.EncryptedStringConverter;
import ke.sauti.hms.common.persistence.BaseEntity;

/** A later addition to a signed note, encrypted at rest and never changed. */
@Entity
@Table(name = "note_addendum")
public class NoteAddendum extends BaseEntity {

    @Column(name = "note_id", nullable = false, updatable = false)
    private UUID noteId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, updatable = false)
    private String body;

    protected NoteAddendum() {
        // for JPA
    }

    NoteAddendum(UUID noteId, UUID authorId, String body) {
        this.noteId = noteId;
        this.authorId = authorId;
        this.body = body;
    }

    public UUID getNoteId() {
        return noteId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getBody() {
        return body;
    }
}
