package ke.sauti.hms.patient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByPatientNumber(long patientNumber);

    List<Patient> findByPhoneHashOrderByFullName(byte[] phoneHash);

    Optional<Patient> findByNationalIdHash(byte[] nationalIdHash);

    /**
     * Names containing the text, case-insensitively; served by the trigram index on
     * {@code lower(full_name)}. The pattern must already be lower-case and escaped.
     */
    @Query("select p from Patient p where lower(p.fullName) like :pattern escape '\\' order by p.fullName")
    List<Patient> findByNameContaining(@Param("pattern") String pattern, Pageable page);
}
