package ke.sauti.hms.visit;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface VisitRepository extends JpaRepository<Visit, UUID> {

    /**
     * Loads the visit with a row lock ({@code select ... for update}) held until the
     * transaction ends, so two people acting on one visit at once go one after the other:
     * the second sees what the first did.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select v from Visit v where v.id = :id")
    Optional<Visit> findByIdForUpdate(@Param("id") UUID id);

    @Query("select v from Visit v where v.patientId = :patientId and v.status not in"
            + " (ke.sauti.hms.visit.VisitStatus.DONE, ke.sauti.hms.visit.VisitStatus.CANCELLED)")
    Optional<Visit> findOpenByPatientId(@Param("patientId") UUID patientId);

    List<Visit> findByVisitDateAndStatusOrderByQueueNumberAsc(LocalDate visitDate, VisitStatus status);
}
