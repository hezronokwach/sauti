package ke.sauti.hms.visit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CareAssignmentRepository extends JpaRepository<CareAssignment, UUID> {

    List<CareAssignment> findByVisitIdAndEndedAtIsNull(UUID visitId);

    List<CareAssignment> findByVisitIdOrderByStartedAtAsc(UUID visitId);

    List<CareAssignment> findByVisitIdAndStaffId(UUID visitId, UUID staffId);

    /** This person's assignments on any visit of the patient. */
    @Query("select a from CareAssignment a, Visit v where v.id = a.visitId and v.patientId = :patientId"
            + " and a.staffId = :staffId")
    List<CareAssignment> findForPatientAndStaff(@Param("patientId") UUID patientId, @Param("staffId") UUID staffId);

    /** Open assignments of this person on visits of the patient that are still in progress. */
    @Query("select a from CareAssignment a, Visit v where v.id = a.visitId and v.patientId = :patientId"
            + " and a.staffId = :staffId and a.endedAt is null and v.status not in"
            + " (ke.sauti.hms.visit.VisitStatus.DONE, ke.sauti.hms.visit.VisitStatus.CANCELLED)")
    List<CareAssignment> findOpenOnCurrentVisits(@Param("patientId") UUID patientId, @Param("staffId") UUID staffId);
}
