package ke.sauti.hms.queue.dto;

import java.util.List;
import ke.sauti.hms.queue.QueueBoard;

/**
 * The public waiting-area display: ticket codes only, never names.
 *
 * @param called the last codes called, newest first
 * @param waiting the codes still waiting, in arrival order
 */
public record QueueBoardResponse(List<String> called, List<String> waiting) {

    public static QueueBoardResponse of(QueueBoard board) {
        return new QueueBoardResponse(board.called(), board.waiting());
    }
}
