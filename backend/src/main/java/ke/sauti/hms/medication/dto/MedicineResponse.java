package ke.sauti.hms.medication.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import ke.sauti.hms.medication.Medicine;

/**
 * A medicine as the search shows it.
 *
 * @param id the medicine id
 * @param name the generic name
 * @param form tablet, syrup, injection and so on
 * @param strength the strength as labelled
 * @param routes the routes it can be given by
 * @param priceKes the list price in Kenyan shillings
 */
public record MedicineResponse(
        UUID id, String name, String form, String strength, List<String> routes, BigDecimal priceKes) {

    public static MedicineResponse of(Medicine medicine) {
        return new MedicineResponse(
                medicine.getId(),
                medicine.getName(),
                medicine.getForm(),
                medicine.getStrength(),
                medicine.routeList(),
                medicine.getPriceKes());
    }
}
