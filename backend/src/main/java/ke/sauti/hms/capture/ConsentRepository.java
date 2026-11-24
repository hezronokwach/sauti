package ke.sauti.hms.capture;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Consent decisions: insert and read only. */
public interface ConsentRepository extends Repository<Consent, UUID> {

    Consent save(Consent consent);

    List<Consent> findByVisitIdOrderByRecordedAtAsc(UUID visitId);

    /** The decision that counts: the latest for this visit and purpose. */
    Optional<Consent> findFirstByVisitIdAndPurposeOrderByRecordedAtDescIdDesc(UUID visitId, ConsentPurpose purpose);
}
