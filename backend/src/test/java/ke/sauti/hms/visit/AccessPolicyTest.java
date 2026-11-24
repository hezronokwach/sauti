package ke.sauti.hms.visit;

import static ke.sauti.hms.support.ActingAs.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.common.error.ForbiddenException;
import ke.sauti.hms.patient.AllergyService;
import ke.sauti.hms.patient.PatientService;
import ke.sauti.hms.patient.Sex;
import ke.sauti.hms.patient.dto.RegisterPatientRequest;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.staff.StaffRepository;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.support.ActingAs;
import ke.sauti.hms.support.Allergies;
import ke.sauti.hms.support.FixedClockConfiguration;
import ke.sauti.hms.support.MutableClock;
import ke.sauti.hms.support.MutableClockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Who may open clinical data, decided by role and care team, with every decision recorded. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
@ActiveProfiles("test")
class AccessPolicyTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private PatientService patients;

    @Autowired
    private VisitService visits;

    @Autowired
    private AllergyService allergies;

    @Autowired
    private AccessPolicy policy;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    private Staff reception;
    private Staff nurse;
    private Staff doctor;

    @BeforeEach
    void setUp() {
        clock.set(FixedClockConfiguration.NOW);
        reception = ActingAs.staff(staffRepository, StaffRole.RECEPTIONIST);
        nurse = ActingAs.staff(staffRepository, StaffRole.NURSE);
        doctor = ActingAs.staff(staffRepository, StaffRole.DOCTOR);
    }

    private static VisitService.Actor actor(Staff staff) {
        return new VisitService.Actor(staff.getId(), staff.getRole());
    }

    private UUID newPatient() {
        return patients.register(new RegisterPatientRequest(
                        "Policy Patient",
                        LocalDate.of(1992, 8, 9),
                        Sex.FEMALE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null))
                .getId();
    }

    /** Opens a visit and leaves it with the nurse in triage. */
    private UUID visitInTriage(UUID patientId) {
        UUID visitId =
                visits.open(patientId, "Maumivu ya kichwa", actor(reception)).getId();
        visits.changeStatus(visitId, VisitStatus.WAITING_TRIAGE, null, actor(reception));
        visits.take(visitId, actor(nurse));
        return visitId;
    }

    private long rows(String sql, Object... args) {
        Long count = jdbc.queryForObject(sql, Long.class, args);
        return count == null ? 0 : count;
    }

    @Test
    void anUnassignedNurseIsRefusedAndTheRefusalIsAudited() throws Exception {
        UUID patientId = newPatient();
        visitInTriage(patientId);
        Staff otherNurse = ActingAs.staff(staffRepository, StaffRole.NURSE);

        mvc.perform(get("/api/patients/" + patientId + "/allergies").with(as(otherNurse)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You are not looking after this patient"));

        assertThat(rows(
                        "select count(*) from audit_event where action = 'ACCESS_DENIED' and actor_id = ? and patient_id = ?",
                        otherNurse.getId(),
                        patientId))
                .isEqualTo(1);
        assertThat(rows("select count(*) from access_log where actor_id = ?", otherNurse.getId()))
                .isZero();
    }

    @Test
    void theNurseLookingAfterThePatientReadsAndTheReadIsLogged() throws Exception {
        UUID patientId = newPatient();
        visitInTriage(patientId);

        mvc.perform(get("/api/patients/" + patientId + "/allergies").with(as(nurse)))
                .andExpect(status().isOk());

        assertThat(rows(
                        "select count(*) from access_log where actor_id = ? and patient_id = ? and what = 'ALLERGIES_VIEWED'",
                        nurse.getId(),
                        patientId))
                .isEqualTo(1);
    }

    @Test
    void accessLastsTwentyFourHoursAfterHandingThePatientOn() {
        UUID patientId = newPatient();
        UUID visitId = visitInTriage(patientId);
        allergies.record(visits.get(visitId).getPatientId(), Allergies.NONE_KNOWN, actor(nurse));
        visits.changeStatus(visitId, VisitStatus.WAITING_DOCTOR, null, actor(nurse));

        clock.advance(Duration.ofHours(23));
        assertThatCode(() -> policy.requirePatientAccess(actor(nurse), patientId, ClinicalData.ALLERGIES, false))
                .doesNotThrowAnyException();

        clock.advance(Duration.ofHours(2));
        assertThatThrownBy(() -> policy.requirePatientAccess(actor(nurse), patientId, ClinicalData.ALLERGIES, false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void theReasonForVisitIsForTheCareTeamOnly() throws Exception {
        UUID visitId = visitInTriage(newPatient());

        mvc.perform(get("/api/visits/" + visitId + "/reason").with(as(nurse)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonForVisit").value("Maumivu ya kichwa"));
        mvc.perform(get("/api/visits/" + visitId + "/reason").with(as(reception)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/visits/" + visitId + "/reason").with(as(doctor))).andExpect(status().isForbidden());
        mvc.perform(get("/api/visits/" + visitId + "/reason")
                        .with(as(ActingAs.staff(staffRepository, StaffRole.CASHIER))))
                .andExpect(status().isForbidden());

        assertThat(rows(
                        "select count(*) from access_log where visit_id = ? and what = 'REASON_FOR_VISIT_VIEWED'",
                        visitId))
                .isEqualTo(2);
    }

    @Test
    void aDoctorOnTheCurrentVisitMayOpenPastSignedRecordsButNotOthersDataFromThem() {
        UUID patientId = newPatient();
        UUID oldVisit = visitInTriage(patientId);
        allergies.record(visits.get(oldVisit).getPatientId(), Allergies.NONE_KNOWN, actor(nurse));
        visits.changeStatus(oldVisit, VisitStatus.WAITING_DOCTOR, null, actor(nurse));
        Staff firstDoctor = ActingAs.staff(staffRepository, StaffRole.DOCTOR);
        visits.take(oldVisit, actor(firstDoctor));
        visits.changeStatus(oldVisit, VisitStatus.WAITING_PAYMENT, null, actor(firstDoctor));
        visits.changeStatus(
                oldVisit, VisitStatus.DONE, null, actor(ActingAs.staff(staffRepository, StaffRole.CASHIER)));
        clock.advance(Duration.ofDays(30));

        UUID newVisit = visitInTriage(patientId);
        allergies.record(visits.get(newVisit).getPatientId(), Allergies.NONE_KNOWN, actor(nurse));
        visits.changeStatus(newVisit, VisitStatus.WAITING_DOCTOR, null, actor(nurse));
        visits.take(newVisit, actor(doctor));

        assertThatCode(() -> policy.requireVisitAccess(actor(doctor), oldVisit, ClinicalData.PAST_SIGNED_NOTE, false))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireVisitAccess(actor(doctor), oldVisit, ClinicalData.TRANSCRIPT, false))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(
                        () -> policy.requireVisitAccess(actor(nurse), oldVisit, ClinicalData.PAST_SIGNED_NOTE, false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void theRoleTableKeepsCashiersFromNotesAndAdminsFromEverything() {
        for (ClinicalData kind : ClinicalData.values()) {
            assertThat(AccessPolicy.roleMay(StaffRole.ADMIN, kind))
                    .as("admin " + kind)
                    .isFalse();
        }
        assertThat(AccessPolicy.roleMay(StaffRole.CASHIER, ClinicalData.NOTE)).isFalse();
        assertThat(AccessPolicy.roleMay(StaffRole.CASHIER, ClinicalData.TRANSCRIPT))
                .isFalse();
        assertThat(AccessPolicy.roleMay(StaffRole.RECEPTIONIST, ClinicalData.ALLERGIES))
                .isFalse();
        assertThat(AccessPolicy.roleMay(StaffRole.DOCTOR, ClinicalData.AUDIO)).isTrue();
    }
}
