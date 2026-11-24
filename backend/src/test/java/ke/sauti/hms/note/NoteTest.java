package ke.sauti.hms.note;

import static ke.sauti.hms.support.ActingAs.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.patient.PatientService;
import ke.sauti.hms.patient.Sex;
import ke.sauti.hms.patient.dto.RegisterPatientRequest;
import ke.sauti.hms.security.totp.TotpGenerator;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.staff.StaffRepository;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.support.ActingAs;
import ke.sauti.hms.visit.VisitService;
import ke.sauti.hms.visit.VisitStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/** The doctor's note: safe drafts, signing with a code, nothing changing after, addenda, history. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class NoteTest {

    private static final JsonMapper JSON = JsonMapper.shared();
    private static final String SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private PatientService patients;

    @Autowired
    private VisitService visits;

    @Autowired
    private TotpGenerator totp;

    @Autowired
    private JdbcTemplate jdbc;

    private Staff reception;
    private Staff nurse;
    private Staff doctor;
    private Staff cashier;
    private UUID patientId;

    @BeforeEach
    void setUp() {
        reception = ActingAs.staff(staffRepository, StaffRole.RECEPTIONIST);
        nurse = ActingAs.staff(staffRepository, StaffRole.NURSE);
        doctor = doctorWithAuthenticator();
        cashier = ActingAs.staff(staffRepository, StaffRole.CASHIER);
        patientId = patients.register(new RegisterPatientRequest(
                        "Note Patient",
                        LocalDate.of(1968, 3, 9),
                        Sex.MALE,
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

    private Staff doctorWithAuthenticator() {
        Staff staff = ActingAs.staff(staffRepository, StaffRole.DOCTOR);
        staff.startAuthenticatorSetup(SECRET);
        staff.confirmAuthenticator();
        return staffRepository.save(staff);
    }

    private String code() {
        return totp.codeFor(SECRET, totp.currentStep());
    }

    private static VisitService.Actor actor(Staff staff) {
        return new VisitService.Actor(staff.getId(), staff.getRole());
    }

    /** A visit of the patient, now with this doctor. */
    private UUID visitWith(Staff withDoctor) {
        UUID visitId = visits.open(patientId, null, actor(reception)).getId();
        visits.changeStatus(visitId, VisitStatus.WAITING_TRIAGE, null, actor(reception));
        visits.take(visitId, actor(nurse));
        visits.changeStatus(visitId, VisitStatus.WAITING_DOCTOR, null, actor(nurse));
        visits.take(visitId, actor(withDoctor));
        return visitId;
    }

    private void finish(UUID visitId, Staff byDoctor) {
        visits.changeStatus(visitId, VisitStatus.WAITING_PAYMENT, null, actor(byDoctor));
        visits.changeStatus(visitId, VisitStatus.DONE, null, actor(cashier));
    }

    private static Map<String, Object> draft(Long version, String assessment) {
        Map<String, Object> body = new HashMap<>();
        body.put("version", version);
        body.put("presentingComplaint", "Homa kwa siku tatu, headache");
        body.put("examination", "Temp 38.5, chest clear");
        body.put("assessment", assessment);
        body.put("plan", assessment == null ? null : "Paracetamol 1 g three times a day for 3 days");
        return body;
    }

    private ResultActions save(Staff who, UUID visitId, Map<String, Object> body) throws Exception {
        return mvc.perform(put("/api/visits/" + visitId + "/note")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body))
                .with(as(who)));
    }

    private ResultActions sign(Staff who, UUID visitId, long version, String code) throws Exception {
        return mvc.perform(post("/api/visits/" + visitId + "/note/sign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("version", version, "code", code)))
                .with(as(who)));
    }

    private long versionOf(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString())
                .get("version")
                .asLong();
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    @Test
    void draftsSaveWithTheHeldVersionAndAStaleVersionIsRefused() throws Exception {
        UUID visitId = visitWith(doctor);

        long first = versionOf(save(doctor, visitId, draft(null, "Malaria?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT")));
        long second = versionOf(save(doctor, visitId, draft(first, "Malaria, uncomplicated"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessment").value("Malaria, uncomplicated")));
        assertThat(second).isGreaterThan(first);

        // Another tab still holding the first version, and one that never loaded the note.
        save(doctor, visitId, draft(first, "Overwrite"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
        save(doctor, visitId, draft(null, "Overwrite"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));

        mvc.perform(get("/api/visits/" + visitId + "/note").with(as(doctor)))
                .andExpect(jsonPath("$.assessment").value("Malaria, uncomplicated"));
    }

    @Test
    void theSectionsAreEncryptedInTheDatabase() throws Exception {
        UUID visitId = visitWith(doctor);
        save(doctor, visitId, draft(null, "Malaria, uncomplicated")).andExpect(status().isOk());

        byte[] stored =
                jdbc.queryForObject("select assessment from clinical_note where visit_id = ?", byte[].class, visitId);

        assertThat(stored).isNotNull();
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("Malaria");
    }

    @Test
    void signingNeedsAValidCodeAndThenTheNoteNeverChanges() throws Exception {
        UUID visitId = visitWith(doctor);
        long version = versionOf(save(doctor, visitId, draft(null, "Malaria, uncomplicated")));

        sign(doctor, visitId, version, "000000").andExpect(status().isForbidden());
        mvc.perform(get("/api/visits/" + visitId + "/note").with(as(doctor)))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        long signedVersion = versionOf(sign(doctor, visitId, version, code())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNED"))
                .andExpect(jsonPath("$.signedBy").value(doctor.getId().toString())));
        assertThat(count("select count(*) from audit_event where action = 'NOTE_SIGNED' and visit_id = ?", visitId))
                .isEqualTo(1);

        save(doctor, visitId, draft(signedVersion, "Changed after signing"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        sign(doctor, visitId, signedVersion, code()).andExpect(status().isConflict());
        mvc.perform(get("/api/visits/" + visitId + "/note").with(as(doctor)))
                .andExpect(jsonPath("$.assessment").value("Malaria, uncomplicated"));
    }

    @Test
    void assessmentAndPlanAreNeededToSign() throws Exception {
        UUID visitId = visitWith(doctor);
        long version = versionOf(save(doctor, visitId, draft(null, null)));

        sign(doctor, visitId, version, code())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Write the assessment and the plan before signing"));
    }

    @Test
    void signingAnOlderVersionThanTheCurrentIsRefused() throws Exception {
        UUID visitId = visitWith(doctor);
        long first = versionOf(save(doctor, visitId, draft(null, "Malaria?")));
        save(doctor, visitId, draft(first, "Malaria, uncomplicated"));

        sign(doctor, visitId, first, code())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    @Test
    void addendaGoOnlyOnSignedNotesAndAreAudited() throws Exception {
        UUID visitId = visitWith(doctor);
        long version = versionOf(save(doctor, visitId, draft(null, "Malaria, uncomplicated")));
        String addendum = "{\"text\":\"Malaria test came back positive\"}";

        mvc.perform(post("/api/visits/" + visitId + "/note/addenda")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addendum)
                        .with(as(doctor)))
                .andExpect(status().isConflict());

        sign(doctor, visitId, version, code()).andExpect(status().isOk());
        mvc.perform(post("/api/visits/" + visitId + "/note/addenda")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addendum)
                        .with(as(doctor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addenda[0].text").value("Malaria test came back positive"));

        assertThat(count(
                        "select count(*) from audit_event where action = 'NOTE_ADDENDUM_ADDED' and visit_id = ?",
                        visitId))
                .isEqualTo(1);
    }

    @Test
    void onlyTheCareTeamReadsAndOnlyTheirDoctorWrites() throws Exception {
        UUID visitId = visitWith(doctor);
        save(doctor, visitId, draft(null, "Malaria?")).andExpect(status().isOk());
        Staff otherDoctor = ActingAs.staff(staffRepository, StaffRole.DOCTOR);

        mvc.perform(get("/api/visits/" + visitId + "/note").with(as(nurse))).andExpect(status().isOk());
        save(nurse, visitId, draft(null, "Nurse edit")).andExpect(status().isForbidden());
        mvc.perform(get("/api/visits/" + visitId + "/note").with(as(otherDoctor)))
                .andExpect(status().isForbidden());
        save(otherDoctor, visitId, draft(null, "Other doctor")).andExpect(status().isForbidden());

        assertThat(count("select count(*) from access_log where actor_id = ? and what = 'NOTE_OPENED'", nurse.getId()))
                .isEqualTo(1);
    }

    @Test
    void historyShowsEarlierSignedNotesToTheDoctorSeeingThePatientNow() throws Exception {
        UUID earlier = visitWith(doctor);
        long version = versionOf(save(doctor, earlier, draft(null, "Malaria, uncomplicated")));
        sign(doctor, earlier, version, code()).andExpect(status().isOk());
        finish(earlier, doctor);

        Staff nextDoctor = ActingAs.staff(staffRepository, StaffRole.DOCTOR);
        Staff unrelatedDoctor = ActingAs.staff(staffRepository, StaffRole.DOCTOR);
        UUID current = visitWith(nextDoctor);
        save(nextDoctor, current, draft(null, "Follow-up")).andExpect(status().isOk());

        mvc.perform(get("/api/patients/" + patientId + "/history").with(as(nextDoctor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].visitId").value(earlier.toString()))
                .andExpect(jsonPath("$[0].assessment").value("Malaria, uncomplicated"));
        mvc.perform(get("/api/patients/" + patientId + "/history").with(as(unrelatedDoctor)))
                .andExpect(status().isForbidden());

        assertThat(count(
                        "select count(*) from access_log where actor_id = ? and what = 'NOTE_OPENED' and visit_id = ?",
                        nextDoctor.getId(),
                        earlier))
                .isEqualTo(1);
    }
}
