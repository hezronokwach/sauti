package ke.sauti.hms.visit.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ke.sauti.hms.visit.VisitStatus;
import org.jspecify.annotations.Nullable;

/**
 * Moving a visit to its next status.
 *
 * @param to the new status
 * @param reason required when cancelling
 */
public record ChangeVisitStatusRequest(
        @NotNull VisitStatus to, @Nullable @Size(max = 200) String reason) {}
