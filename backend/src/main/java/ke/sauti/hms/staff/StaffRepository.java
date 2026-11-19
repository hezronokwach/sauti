package ke.sauti.hms.staff;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffRepository extends JpaRepository<Staff, UUID> {

    /** Exact match on the stored, normalised email. Prefer {@link #findByEmailIgnoreCase}. */
    Optional<Staff> findByEmail(String email);

    /** Finds a staff member by email regardless of case or surrounding spaces. */
    default Optional<Staff> findByEmailIgnoreCase(String email) {
        return findByEmail(Staff.normaliseEmail(email));
    }
}
