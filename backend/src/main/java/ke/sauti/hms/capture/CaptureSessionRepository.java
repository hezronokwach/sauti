package ke.sauti.hms.capture;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaptureSessionRepository extends JpaRepository<CaptureSession, UUID> {

    List<CaptureSession> findByVisitIdAndPurposeAndStatus(UUID visitId, ConsentPurpose purpose, CaptureStatus status);

    List<CaptureSession> findByVisitIdAndPurposeAndStatusNotOrderByStartedAtAsc(
            UUID visitId, ConsentPurpose purpose, CaptureStatus status);
}
