package ke.sauti.hms.demo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ke.sauti.hms.patient.DemoPatients;
import ke.sauti.hms.patient.PayerType;
import ke.sauti.hms.patient.PreferredLanguage;
import ke.sauti.hms.patient.Sex;
import ke.sauti.hms.patient.dto.RegisterPatientRequest;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.staff.StaffAdminService;
import ke.sauti.hms.staff.StaffRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Fills the demo with twenty made-up patients, national IDs 31000001 to 31000020. These are
 * separate from the fake registry's people (30000001 to 30000020), who stay unregistered so
 * the demo can show the registry filling in a new patient. Safe to run at every start: anyone
 * already there is skipped. It creates no staff accounts, so the demo has no shared password;
 * the admin creates them, and each person sets a password and authenticator.
 */
@Component
@ConditionalOnProperty(name = "sauti.demo.seed", havingValue = "true")
public class DemoSeed {

    /** The patient the demo script prescribes amoxicillin for. */
    public static final String PENICILLIN_ALLERGY_ID = "31000001";

    private static final Logger log = LoggerFactory.getLogger(DemoSeed.class);

    private record MadeUpPatient(String nationalId, String fullName, String born, Sex sex) {}

    private static final List<MadeUpPatient> PATIENTS = List.of(
            new MadeUpPatient("31000001", "Demo Neema Wafula", "1984-02-11", Sex.FEMALE),
            new MadeUpPatient("31000002", "Demo Baraka Simiyu", "1992-06-21", Sex.MALE),
            new MadeUpPatient("31000003", "Demo Zuhura Mwinyi", "1976-10-03", Sex.FEMALE),
            new MadeUpPatient("31000004", "Demo Tumaini Barasa", "1968-01-17", Sex.MALE),
            new MadeUpPatient("31000005", "Demo Imani Nekesa", "2000-04-29", Sex.FEMALE),
            new MadeUpPatient("31000006", "Demo Jabali Wekesa", "1996-08-08", Sex.MALE),
            new MadeUpPatient("31000007", "Demo Subira Khaemba", "1989-12-14", Sex.FEMALE),
            new MadeUpPatient("31000008", "Demo Amani Lumumba", "1959-03-26", Sex.MALE),
            new MadeUpPatient("31000009", "Demo Rehema Nafula", "1997-07-07", Sex.FEMALE),
            new MadeUpPatient("31000010", "Demo Jelani Masinde", "1982-05-19", Sex.MALE),
            new MadeUpPatient("31000011", "Demo Upendo Naliaka", "1971-09-01", Sex.FEMALE),
            new MadeUpPatient("31000012", "Demo Bahati Mukhwana", "1963-11-30", Sex.MALE),
            new MadeUpPatient("31000013", "Demo Furaha Nanjala", "2002-02-02", Sex.FEMALE),
            new MadeUpPatient("31000014", "Demo Hodari Wanyama", "1990-10-20", Sex.MALE),
            new MadeUpPatient("31000015", "Demo Pendo Makokha", "1987-06-13", Sex.FEMALE),
            new MadeUpPatient("31000016", "Demo Jasiri Opicho", "1974-04-04", Sex.MALE),
            new MadeUpPatient("31000017", "Demo Tabasamu Nasimiyu", "1994-01-25", Sex.FEMALE),
            new MadeUpPatient("31000018", "Demo Shujaa Wamalwa", "1979-08-16", Sex.MALE),
            new MadeUpPatient("31000019", "Demo Nuru Nabwire", "1999-03-09", Sex.FEMALE),
            new MadeUpPatient("31000020", "Demo Kito Sifuna", "1966-12-05", Sex.MALE));

    private final DemoPatients patients;
    private final StaffAdminService staff;

    public DemoSeed(DemoPatients patients, StaffAdminService staff) {
        this.patients = patients;
        this.staff = staff;
    }

    /** Runs once the app is ready, after the first admin has been created. */
    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        Optional<UUID> admin = staff.list().stream()
                .filter(member -> member.getRole() == StaffRole.ADMIN)
                .map(Staff::getId)
                .findFirst();
        if (admin.isEmpty()) {
            log.warn("Demo patients were not added because there is no admin yet");
            return;
        }
        long added =
                PATIENTS.stream().filter(patient -> add(patient, admin.get())).count();
        log.info("Demo seed added {} of {} made-up patients", added, PATIENTS.size());
    }

    private boolean add(MadeUpPatient patient, UUID adminId) {
        // No phone numbers: a made-up number could belong to a real person.
        RegisterPatientRequest request = new RegisterPatientRequest(
                patient.fullName(),
                LocalDate.parse(patient.born()),
                patient.sex(),
                null,
                patient.nationalId(),
                null,
                null,
                null,
                null,
                PreferredLanguage.SWAHILI,
                PayerType.CASH,
                false);
        return patients.addIfMissing(request, PENICILLIN_ALLERGY_ID.equals(patient.nationalId()), adminId);
    }
}
