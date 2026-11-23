package ke.sauti.hms.medication;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MedicineRepository extends JpaRepository<Medicine, UUID> {

    /** Medicines whose name contains the text, names starting with it first. */
    @Query("""
            select m from Medicine m
            where lower(m.name) like concat('%', ?1, '%') escape '\\'
            order by case when lower(m.name) like concat(?1, '%') escape '\\' then 0 else 1 end,
                     m.name, m.strength
            """)
    List<Medicine> search(String escapedLowerCaseText, Pageable page);
}
