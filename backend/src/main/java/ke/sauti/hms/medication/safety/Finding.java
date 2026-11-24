package ke.sauti.hms.medication.safety;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One problem a rule found.
 *
 * @param rule the rule, R1 to R8
 * @param level how serious it is
 * @param itemId the prescription line; empty when it concerns the whole prescription
 * @param message what the doctor reads. It states the problem and never suggests another medicine
 */
public record Finding(
        String rule, AlertLevel level, @Nullable UUID itemId, String message) {}
