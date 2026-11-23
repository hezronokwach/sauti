package ke.sauti.hms.medication.safety;

/** How serious an alert is. */
public enum AlertLevel {
    /** Stops signing. R1 can never be overridden; the others need a typed reason and a code. */
    BLOCKING,
    /** The doctor must acknowledge it before signing. */
    REVIEW
}
