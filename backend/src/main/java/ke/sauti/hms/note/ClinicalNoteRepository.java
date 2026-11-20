package ke.sauti.hms.note;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ClinicalNoteRepository extends JpaRepository<ClinicalNote, UUID> {

    Optional<ClinicalNote> findByVisitId(UUID visitId);

    /** The note with a row lock, so a save and a sign cannot interleave. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from ClinicalNote n where n.visitId = ?1")
    Optional<ClinicalNote> lockByVisitId(UUID visitId);

    List<ClinicalNote> findByPatientIdAndStatusOrderBySignedAtDesc(UUID patientId, NoteStatus status);
}
