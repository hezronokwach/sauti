package ke.sauti.hms.billing.dto;

import java.math.BigDecimal;
import ke.sauti.hms.billing.InvoiceLine;
import ke.sauti.hms.billing.InvoiceLineKind;

/** One charge as the cashier sees it: never a dose, frequency or instruction. */
public record InvoiceLineResponse(
        InvoiceLineKind kind, String description, int quantity, BigDecimal unitPriceKes, BigDecimal amountKes) {

    public static InvoiceLineResponse of(InvoiceLine line) {
        return new InvoiceLineResponse(
                line.getKind(), line.getDescription(), line.getQuantity(), line.getUnitPriceKes(), line.getAmountKes());
    }
}
