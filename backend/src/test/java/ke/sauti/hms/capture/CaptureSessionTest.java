package ke.sauti.hms.capture;

import static ke.sauti.hms.support.ActingAs.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.patient.AllergyService;
import ke.sauti.hms.patient.PatientService;
import ke.sauti.hms.patient.Sex;
import ke.sauti.hms.patient.dto.RegisterPatientRequest;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.staff.StaffRepository;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.support.ActingAs;
import ke.sauti.hms.support.Allergies;
import ke.sauti.hms.visit.VisitService;
import ke.sauti.hms.visit.VisitStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/** Who may start and stop a recording, and when. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CaptureSessionTest {

    private static final JsonMapper JSON = JsonMapper.shared();

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
    private ConsentService consents;

    @Autowired
    private CaptureSessionRepository sessions;

    private Staff reception;
    private Staff recordingDoctor;

    @BeforeEach
    void setUp() {
        reception = ActingAs.staff(staffRepository, StaffRole.RECEPTIONIST);
        recordingDoctor = ActingAs.staff(staffRepository, StaffRole.DOCTOR);
    }

    private static VisitService.Actor actor(Staff staff) {
        return new VisitService.Actor(staff.getId(), staff.getRole());
    }

    /** A visit that has passed triage and that the doctor has taken. */
    private UUID visitWithDoctor() {
        UUID patientId = patients.register(new RegisterPatientRequest(
                        "Capture Patient",
                        LocalDate.of(1979, 6, 30),
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
        UUID visitId = visits.open(patientId, null, actor(reception)).getId();
        visits.changeStatus(visitId, VisitStatus.WAITING_TRIAGE, null, actor(reception));
        Staff triageNurse = ActingAs.staff(staffRepository, StaffRole.NURSE);
        visits.take(visitId, actor(triageNurse));
        allergies.record(visits.get(visitId).getPatientId(), Allergies.NONE_KNOWN, actor(triageNurse));
        visits.changeStatus(visitId, VisitStatus.WAITING_DOCTOR, null, actor(triageNurse));
        visits.take(visitId, actor(recordingDoctor));
        return visitId;
    }

    private ResultActions start(Staff who, UUID visitId, String purpose) throws Exception {
        return mvc.perform(post("/api/visits/" + visitId + "/captures")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("purpose", purpose, "languageHint", "sw")))
                .with(as(who)));
    }

    private void agree(UUID visitId, Staff who) {
        consents.record(
                visitId,
                ConsentPurpose.RECORD_CONSULTATION,
                ConsentDecision.AGREED,
                ConsentGivenBy.PATIENT,
                actor(who));
    }

    @Test
    void startingWithoutConsentIsRefused() throws Exception {
        UUID visitId = visitWithDoctor();

        start(recordingDoctor, visitId, "RECORD_CONSULTATION")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Recording needs the patient's consent first"));
        assertThat(sessions.findAll()).noneMatch(s -> s.getVisitId().equals(visitId));
    }

    @Test
    void withConsentTheRecordingStartsAndStops() throws Exception {
        UUID visitId = visitWithDoctor();
        agree(visitId, recordingDoctor);

        String id = JSON.readTree(start(recordingDoctor, visitId, "RECORD_CONSULTATION")
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("RECORDING"))
                        .andExpect(jsonPath("$.languageHint").value("sw"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asString();

        mvc.perform(post("/api/captures/" + id + "/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedPieces\":4}")
                        .with(as(recordingDoctor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STOPPED"))
                .andExpect(jsonPath("$.expectedPieces").value(4));
    }

    @Test
    void oneRecordingAtATimePerVisitAndPurpose() throws Exception {
        UUID visitId = visitWithDoctor();
        agree(visitId, recordingDoctor);
        start(recordingDoctor, visitId, "RECORD_CONSULTATION").andExpect(status().isCreated());

        start(recordingDoctor, visitId, "RECORD_CONSULTATION").andExpect(status().isConflict());
    }

    @Test
    void theRoleMustFitThePurpose() throws Exception {
        UUID visitId = visitWithDoctor();
        agree(visitId, recordingDoctor);

        start(reception, visitId, "RECORD_CONSULTATION")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Your role does not make this kind of recording"));
    }

    @Test
    void aNurseCannotRecord() throws Exception {
        UUID visitId = visitWithDoctor();
        agree(visitId, recordingDoctor);

        start(ActingAs.staff(staffRepository, StaffRole.NURSE), visitId, "RECORD_CONSULTATION")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Your role does not make this kind of recording"));
    }

    @Test
    void someoneNotLookingAfterThePatientCannotRecord() throws Exception {
        UUID visitId = visitWithDoctor();
        agree(visitId, recordingDoctor);

        start(ActingAs.staff(staffRepository, StaffRole.DOCTOR), visitId, "RECORD_CONSULTATION")
                .andExpect(status().isForbidden());
    }

    @Test
    void withdrawingConsentStopsTheRecording() throws Exception {
        UUID visitId = visitWithDoctor();
        agree(visitId, recordingDoctor);
        UUID sessionId = UUID.fromString(JSON.readTree(start(recordingDoctor, visitId, "RECORD_CONSULTATION")
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asString());

        consents.record(
                visitId,
                ConsentPurpose.RECORD_CONSULTATION,
                ConsentDecision.WITHDRAWN,
                ConsentGivenBy.PATIENT,
                actor(recordingDoctor));

        CaptureSession session = sessions.findById(sessionId).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(CaptureStatus.STOPPED);
        start(recordingDoctor, visitId, "RECORD_CONSULTATION").andExpect(status().isUnprocessableContent());
    }
}
