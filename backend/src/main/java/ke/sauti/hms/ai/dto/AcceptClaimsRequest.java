package ke.sauti.hms.ai.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Claims from one summary to bring into the note. The server looks them up in its own
 * record of the summary, so only claims that passed its checks can be accepted.
 *
 * @param claims positions of the claims in the summary, from 0
 * @param noteVersion the note version the editor holds; absent if no note was saved yet
 */
public record AcceptClaimsRequest(
        @NotEmpty @Size(max = 50) List<@PositiveOrZero Integer> claims,
        @Nullable @PositiveOrZero Long noteVersion) {}
