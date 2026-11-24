package ke.sauti.hms.queue;

import java.util.List;

/**
 * What the waiting area may see: ticket codes only.
 *
 * @param called the last codes called, newest first
 * @param waiting the codes still waiting, in arrival order
 */
public record QueueBoard(List<String> called, List<String> waiting) {}
