package ke.sauti.hms.queue;

/**
 * Arrival lanes, each with the letter its ticket codes start with. Pre-registered and
 * returning patients are called first; a new patient never waits more than 15 minutes;
 * emergencies skip reception's queue and go straight to clinical staff.
 */
public enum TicketLane {
    PRE_REGISTERED('P'),
    RETURNING('R'),
    NEW('N'),
    EMERGENCY('E');

    private final char letter;

    TicketLane(char letter) {
        this.letter = letter;
    }

    public String code(int number) {
        return letter + "-" + String.format(java.util.Locale.ROOT, "%03d", number);
    }
}
