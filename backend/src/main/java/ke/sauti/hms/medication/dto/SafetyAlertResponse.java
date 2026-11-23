package ke.sauti.hms.medication.dto;

import java.util.UUID;
import ke.sauti.hms.medication.SafetyAlert;
import ke.sauti.hms.medication.safety.AlertLevel;
import ke.sauti.hms.medication.safety.AlertStatus;
import org.jspecify.annotations.Nullable;

/**
 * One safety alert.
 *
 * @param id the alert id
 * @param itemId the prescription line; empty when it concerns the whole prescription
 * @param rule the rule that raised it
 * @param level BLOCKING or REVIEW
 * @param message what the problem is
 * @param status OPEN, OVERRIDDEN or ACKNOWLEDGED
 * @param overrideReason the doctor's reason, when overridden
 * @param canOverride whether a typed reason can clear it; missing details cannot be overridden
 */
public record SafetyAlertResponse(
        UUID id,
        @Nullable UUID itemId,
        String rule,
        AlertLevel level,
        String message,
        AlertStatus status,
        @Nullable String overrideReason,
        boolean canOverride) {

    public static SafetyAlertResponse of(SafetyAlert alert) {
        return new SafetyAlertResponse(
                alert.getId(),
                alert.getItemId(),
                alert.getRule(),
                alert.getLevel(),
                alert.getMessage(),
                alert.getStatus(),
                alert.getOverrideReason(),
                alert.getLevel() == AlertLevel.BLOCKING && !"R1".equals(alert.getRule()));
    }
}
