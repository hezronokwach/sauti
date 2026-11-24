package ke.sauti.hms.medication;

import java.math.BigDecimal;

/**
 * What billing needs from a signed prescription line, and nothing clinical beyond it.
 *
 * @param description medicine name and strength
 * @param quantity how many are dispensed
 * @param unitPriceKes today's list price
 */
public record BillableMedicine(String description, int quantity, BigDecimal unitPriceKes) {}
