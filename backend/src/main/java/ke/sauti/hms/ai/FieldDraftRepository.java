package ke.sauti.hms.ai;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FieldDraftRepository extends JpaRepository<FieldDraft, UUID> {

    List<FieldDraft> findByVisitIdAndStatusOrderByCreatedAtAsc(UUID visitId, FieldDraft.Status status);

    long deleteByPieceIdAndStatus(UUID pieceId, FieldDraft.Status status);
}
