package ke.sauti.hms.medication;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {

    @Query("""
            select p from Prescription p
            where p.visitId = ?1 and p.status <> ke.sauti.hms.medication.PrescriptionStatus.CANCELLED
            """)
    Optional<Prescription> findActiveByVisitId(UUID visitId);

    /** The active prescription with a row lock, so two item changes cannot interleave. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from Prescription p
            where p.visitId = ?1 and p.status <> ke.sauti.hms.medication.PrescriptionStatus.CANCELLED
            """)
    Optional<Prescription> lockActiveByVisitId(UUID visitId);
}
