package ke.sauti.hms.medication.dto;

import java.util.UUID;

/**
 * One medicine's spoken instruction.
 *
 * @param itemId the prescription line
 * @param text exactly what is spoken, shown on screen too
 */
public record SpokenLineResponse(UUID itemId, String text) {}
