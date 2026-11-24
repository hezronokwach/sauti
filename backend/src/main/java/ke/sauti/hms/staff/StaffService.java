package ke.sauti.hms.staff;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * How other features reach staff records. Entities returned inside the caller's
 * transaction are managed, so changes made through their methods are saved when that
 * transaction commits.
 */
@Service
@Transactional
public class StaffService {

    private final StaffRepository repository;

    public StaffService(StaffRepository repository) {
        this.repository = repository;
    }

    /** Finds a staff member by email regardless of case or surrounding spaces. */
    public Optional<Staff> findByEmail(String email) {
        return repository.findByEmailIgnoreCase(email);
    }

    public Optional<Staff> findById(UUID id) {
        return repository.findById(id);
    }
}
