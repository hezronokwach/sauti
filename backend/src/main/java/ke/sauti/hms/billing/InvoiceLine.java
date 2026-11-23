package ke.sauti.hms.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import ke.sauti.hms.common.crypto.EncryptedStringConverter;
import ke.sauti.hms.common.persistence.BaseEntity;

/** One charge on a bill. A medicine's name is encrypted because it says something clinical. */
@Entity
@Table(name = "invoice_line")
public class InvoiceLine extends BaseEntity {

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    @Column(nullable = false, updatable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private InvoiceLineKind kind;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, updatable = false)
    private String description;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "unit_price_kes", nullable = false, updatable = false)
    private BigDecimal unitPriceKes;

    @Column(name = "amount_kes", nullable = false, updatable = false)
    private BigDecimal amountKes;

    protected InvoiceLine() {
        // for JPA
    }

    InvoiceLine(
            UUID invoiceId,
            int position,
            InvoiceLineKind kind,
            String description,
            int quantity,
            BigDecimal unitPriceKes) {
        this.invoiceId = invoiceId;
        this.position = position;
        this.kind = kind;
        this.description = description;
        this.quantity = quantity;
        this.unitPriceKes = unitPriceKes;
        this.amountKes = unitPriceKes.multiply(BigDecimal.valueOf(quantity));
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public int getPosition() {
        return position;
    }

    public InvoiceLineKind getKind() {
        return kind;
    }

    public String getDescription() {
        return description;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPriceKes() {
        return unitPriceKes;
    }

    public BigDecimal getAmountKes() {
        return amountKes;
    }
}
