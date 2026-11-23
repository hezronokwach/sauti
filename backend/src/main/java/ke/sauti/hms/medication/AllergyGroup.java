package ke.sauti.hms.medication;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** A group of related medicines, such as penicillins. Read-only, like the medicine list. */
@Entity
@Immutable
@Table(name = "allergy_group")
public class AllergyGroup {

    @Id
    private String code;

    @Column(nullable = false)
    private String label;

    protected AllergyGroup() {
        // for JPA
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
