package ke.sauti.hms.capture;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AudioPieceRepository extends JpaRepository<AudioPiece, UUID> {

    List<AudioPiece> findBySessionIdOrderBySequenceNoAsc(UUID sessionId);
}
