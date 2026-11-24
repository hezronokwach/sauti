package ke.sauti.hms.medication;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** One entry of the medicine list. Read-only: the list changes only through a new migration. */
@Entity
@Immutable
@Table(name = "medicine")
public class Medicine {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String form;

    @Column(nullable = false)
    private String strength;

    @Column(name = "group_code", nullable = false)
    private String groupCode;

    @Column(nullable = false)
    private String routes;

    @Column(name = "price_kes", nullable = false)
    private BigDecimal priceKes;

    @Column(name = "list_version", nullable = false)
    private String listVersion;

    protected Medicine() {
        // for JPA
    }

    /** The routes this medicine can be given by. */
    public List<String> routeList() {
        return List.of(routes.split("\\|", -1));
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getForm() {
        return form;
    }

    public String getStrength() {
        return strength;
    }

    public String getGroupCode() {
        return groupCode;
    }

    public BigDecimal getPriceKes() {
        return priceKes;
    }

    public String getListVersion() {
        return listVersion;
    }
}
