package ke.sauti.hms.note;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteAddendumRepository extends JpaRepository<NoteAddendum, UUID> {

    List<NoteAddendum> findByNoteIdOrderByCreatedAtAsc(UUID noteId);
}
