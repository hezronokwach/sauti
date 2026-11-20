package ke.sauti.hms.capture;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TranscriptSegmentRepository extends JpaRepository<TranscriptSegment, UUID> {

    List<TranscriptSegment> findBySessionIdOrderByOrderNoAsc(UUID sessionId);

    /** Segments before a given position, newest first: the context for the next piece. */
    List<TranscriptSegment> findTop5BySessionIdAndOrderNoLessThanOrderByOrderNoDesc(UUID sessionId, int orderNo);

    List<TranscriptSegment> findBySessionIdAndOrderNoGreaterThanOrderByOrderNoAsc(UUID sessionId, int orderNo);

    @Modifying
    @Query("delete from TranscriptSegment s where s.sessionId = :sessionId")
    int deleteBySessionId(@Param("sessionId") UUID sessionId);

    @Modifying
    @Query("delete from TranscriptSegment s where s.pieceId = :pieceId")
    int deleteByPieceId(@Param("pieceId") UUID pieceId);
}
