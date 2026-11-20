package ke.sauti.hms.visit;

import java.util.Set;

/** Where a visit is in its journey. DONE and CANCELLED are final. */
public enum VisitStatus {
    AT_RECEPTION,
    WAITING_TRIAGE,
    IN_TRIAGE,
    WAITING_DOCTOR,
    WITH_DOCTOR,
    WAITING_PAYMENT,
    DONE,
    CANCELLED;

    private static final Set<VisitStatus> FINAL = Set.of(DONE, CANCELLED);

    public boolean isFinal() {
        return FINAL.contains(this);
    }
}
