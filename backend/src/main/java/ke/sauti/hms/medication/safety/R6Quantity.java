package ke.sauti.hms.medication.safety;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ke.sauti.hms.medication.DoseUnit;
import ke.sauti.hms.medication.Frequency;
import ke.sauti.hms.medication.PrescriptionItem;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * R6: the quantity does not cover dose x times per day x days. This is worked out only for
 * tablets, capsules and sachets, where one unit is one countable thing. As-needed medicines,
 * liquids and combination strengths have no reliable total, so only a missing quantity is flagged.
 */
@Component
public class R6Quantity implements SafetyRule {

    private static final Pattern SINGLE_STRENGTH = Pattern.compile("^(\\d+(?:\\.\\d+)?) (mcg|mg|g)$");

    @Override
    public List<Finding> check(RuleInput input) {
        List<Finding> findings = new ArrayList<>();
        for (RuleInput.Line line : input.lines()) {
            PrescriptionItem item = line.item();
            String name = line.medicine().getName();
            if (item.getQuantity() == null) {
                findings.add(new Finding("R6", AlertLevel.REVIEW, item.getId(), name + " has no quantity."));
                continue;
            }
            BigDecimal needed = unitsNeeded(line);
            if (needed != null && BigDecimal.valueOf(item.getQuantity()).compareTo(needed) < 0) {
                findings.add(new Finding(
                        "R6",
                        AlertLevel.REVIEW,
                        item.getId(),
                        name + ": quantity " + item.getQuantity() + " does not cover the course, which needs "
                                + needed.toPlainString() + "."));
            }
        }
        return findings;
    }

    private static @Nullable BigDecimal unitsNeeded(RuleInput.Line line) {
        PrescriptionItem item = line.item();
        if (item.getDoseValue() == null || item.getDoseUnit() == null || item.getFrequency() == null) {
            return null;
        }
        Integer perDay = timesPerDay(item.getFrequency());
        if (perDay == null || (item.getDurationDays() == null && item.getFrequency() != Frequency.STAT)) {
            return null;
        }
        BigDecimal perDose = unitsPerDose(line);
        if (perDose == null) {
            return null;
        }
        int days = item.getFrequency() == Frequency.STAT ? 1 : item.getDurationDays();
        return perDose.multiply(BigDecimal.valueOf((long) perDay * days)).setScale(0, RoundingMode.CEILING);
    }

    private static @Nullable Integer timesPerDay(Frequency frequency) {
        return switch (frequency) {
            case OD, NOCTE, STAT -> 1;
            case BD -> 2;
            case TDS, Q8H -> 3;
            case QID -> 4;
            case AS_NEEDED -> null;
        };
    }

    /** How many tablets, capsules or sachets one dose is; empty when that cannot be known. */
    private static @Nullable BigDecimal unitsPerDose(RuleInput.Line line) {
        String form = line.medicine().getForm().toLowerCase(Locale.ROOT);
        DoseUnit unit = line.item().getDoseUnit();
        BigDecimal dose = line.item().getDoseValue();
        boolean solid = form.equals("tablet") || form.equals("capsule");
        if ((unit == DoseUnit.TABLET || unit == DoseUnit.CAPSULE) && solid) {
            return dose;
        }
        if (unit == DoseUnit.SACHET && form.equals("sachet")) {
            return dose;
        }
        if (!solid || (unit != DoseUnit.MG && unit != DoseUnit.MCG && unit != DoseUnit.G)) {
            return null;
        }
        Matcher strength = SINGLE_STRENGTH.matcher(line.medicine().getStrength());
        if (!strength.matches()) {
            return null;
        }
        BigDecimal perUnit = micrograms(new BigDecimal(strength.group(1)), strength.group(2));
        return micrograms(dose, unit.name().toLowerCase(Locale.ROOT)).divide(perUnit, 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal micrograms(BigDecimal amount, String unit) {
        return switch (unit) {
            case "g" -> amount.multiply(BigDecimal.valueOf(1_000_000));
            case "mg" -> amount.multiply(BigDecimal.valueOf(1_000));
            default -> amount;
        };
    }
}
