package ke.sauti.hms.facility;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import ke.sauti.hms.common.persistence.BaseEntity;

/**
 * The single facility this installation serves: its display name and code.
 * The table allows exactly one row, created by the first migration.
 */
@Entity
@Table(name = "facility_settings")
public class FacilitySettings extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 20)
    private String code;

    protected FacilitySettings() {
        // for JPA
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public void rename(String name, String code) {
        this.name = Objects.requireNonNull(name, "name");
        this.code = Objects.requireNonNull(code, "code");
    }
}
