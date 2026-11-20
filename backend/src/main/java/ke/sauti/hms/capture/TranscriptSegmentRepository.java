package ke.sauti.hms.capture;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TranscriptSegmentRepository extends JpaRepository<TranscriptSegment, UUID> {

    List<TranscriptSegment> findBySessionIdOrderByOrderNoAsc(UUID sessionId);
}
