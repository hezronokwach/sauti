package ke.sauti.hms.staff;

/** What a staff member does, which decides the screens and data they can reach. */
public enum StaffRole {
    RECEPTIONIST,
    NURSE,
    DOCTOR,
    CASHIER,
    ADMIN;

    /** Doctors and nurses must record a professional licence number. */
    public boolean requiresLicence() {
        return this == DOCTOR || this == NURSE;
    }
}
