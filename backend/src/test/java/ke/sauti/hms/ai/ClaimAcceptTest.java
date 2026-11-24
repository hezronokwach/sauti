package ke.sauti.hms.ai;

import static ke.sauti.hms.support.ActingAs.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.capture.ConsentDecision;
import ke.sauti.hms.capture.ConsentGivenBy;
import ke.sauti.hms.capture.ConsentPurpose;
import ke.sauti.hms.capture.ConsentService;
import ke.sauti.hms.common.crypto.ColumnCipher;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Accepting summary claims into the note, with the real fake model: its two transcript
 * claims pass the checks, its planted claim does not and can never be accepted.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ClaimAcceptTest {

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
    private ConsentService consent;

    @Autowired
    private ColumnCipher cipher;

    @Autowired
    private JdbcTemplate jdbc;

    private Staff reception;
    private Staff nurse;
    private Staff doctor;

    @BeforeEach
    void setUp() {
        reception = ActingAs.staff(staffRepository, StaffRole.RECEPTIONIST);
        nurse = ActingAs.staff(staffRepository, StaffRole.NURSE);
        doctor = ActingAs.staff(staffRepository, StaffRole.DOCTOR);
    }

    private static VisitService.Actor actor(Staff staff) {
        return new VisitService.Actor(staff.getId(), staff.getRole());
    }

    /** A visit with the doctor, consent given, and a two-line consultation transcript. */
    private UUID consultation() {
        UUID patientId = patients.register(new RegisterPatientRequest(
                        "Accept Patient",
                        LocalDate.of(1979, 2, 14),
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
        UUID visitId = visits.open(patientId, null, actor(reception)).getId();
        visits.changeStatus(visitId, VisitStatus.WAITING_TRIAGE, null, actor(reception));
        visits.take(visitId, actor(nurse));
        allergies.record(visits.get(visitId).getPatientId(), Allergies.NONE_KNOWN, actor(nurse));
        visits.changeStatus(visitId, VisitStatus.WAITING_DOCTOR, null, actor(nurse));
        visits.take(visitId, actor(doctor));
        consent.record(
                visitId,
                ConsentPurpose.RECORD_CONSULTATION,
                ConsentDecision.AGREED,
                ConsentGivenBy.PATIENT,
                actor(doctor));
        UUID sessionId = UUID.randomUUID();
        UUID pieceId = UUID.randomUUID();
        jdbc.update(
                "insert into capture_session (id, visit_id, purpose, started_by, status, started_at)"
                        + " values (?, ?, 'RECORD_CONSULTATION', ?, 'COMPLETE', now())",
                sessionId,
                visitId,
                doctor.getId());
        jdbc.update(
                "insert into audio_piece (id, session_id, sequence_no, storage_key, duration_ms, offset_ms, sha256, status)"
                        + " values (?, ?, 0, 'test', 30000, 0, ?, 'TRANSCRIBED')",
                pieceId,
                sessionId,
                "0".repeat(64));
        List<String> lines = List.of("Nina homa siku tatu.", "Sina kikohozi.");
        for (int i = 0; i < lines.size(); i++) {
            jdbc.update(
                    "insert into transcript_segment (piece_id, session_id, order_no, start_ms, end_ms, text, confidence)"
                            + " values (?, ?, ?, ?, ?, ?, 'HIGH')",
                    pieceId,
                    sessionId,
                    i,
                    i * 4_000,
                    i * 4_000 + 3_000,
                    cipher.encrypt(lines.get(i).getBytes(StandardCharsets.UTF_8)));
        }
        return visitId;
    }

    private JsonNode summarise(UUID visitId) throws Exception {
        String body = mvc.perform(post("/api/visits/" + visitId + "/summary").with(as(doctor)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JSON.readTree(body);
    }

    private ResultActions accept(Staff who, UUID visitId, String requestId, List<Integer> claims, Long noteVersion)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("claims", claims);
        body.put("noteVersion", noteVersion);
        return mvc.perform(post("/api/visits/" + visitId + "/summary/" + requestId + "/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body))
                .with(as(who)));
    }

    private long noteCount(UUID visitId) {
        Long n = jdbc.queryForObject("select count(*) from clinical_note where visit_id = ?", Long.class, visitId);
        return n == null ? 0 : n;
    }

    @Test
    void theFakeModelsPlantedClaimIsFlaggedAndCannotBeAccepted() throws Exception {
        UUID visitId = consultation();
        JsonNode summary = summarise(visitId);
        JsonNode claims = summary.get("claims");
        int planted = claims.size() - 1;

        assertThat(claims.get(0).get("supported").asBoolean()).isTrue();
        assertThat(claims.get(1).get("supported").asBoolean()).isTrue();
        assertThat(claims.get(planted).get("supported").asBoolean()).isFalse();
        assertThat(claims.get(planted).get("quotePresent").asBoolean()).isFalse();

        String requestId = summary.get("requestId").asString();
        accept(doctor, visitId, requestId, List.of(planted), null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message")
                        .value("A claim that failed the checks cannot be accepted; write it yourself if it is right"));
        // Mixed with good claims, the whole request is refused and nothing is added.
        accept(doctor, visitId, requestId, List.of(0, planted), null).andExpect(status().isUnprocessableContent());
        assertThat(noteCount(visitId)).isZero();
    }

    @Test
    void acceptedClaimsBecomeEditableNoteTextAndMarkTheNoteAiAssisted() throws Exception {
        UUID visitId = consultation();
        JsonNode summary = summarise(visitId);
        String requestId = summary.get("requestId").asString();

        accept(doctor, visitId, requestId, List.of(0, 1), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history").value("Said: Nina homa siku tatu.\nSaid: Sina kikohozi."))
                .andExpect(jsonPath("$.aiAssisted").value(true))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        assertThat(jdbc.queryForObject(
                        "select count(*) from audit_event where action = 'AI_CLAIMS_ACCEPTED' and visit_id = ?",
                        Long.class,
                        visitId))
                .isEqualTo(1);
    }

    @Test
    void acceptingFollowsTheNotesVersionRule() throws Exception {
        UUID visitId = consultation();
        String requestId = summarise(visitId).get("requestId").asString();
        Map<String, Object> draft = new HashMap<>();
        draft.put("version", null);
        draft.put("assessment", "Typed by the doctor");
        mvc.perform(put("/api/visits/" + visitId + "/note")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(draft))
                        .with(as(doctor)))
                .andExpect(status().isOk());

        // The editor must send the version it holds; without it the doctor's typing could be lost.
        accept(doctor, visitId, requestId, List.of(0), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
        accept(doctor, visitId, requestId, List.of(0), 0L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessment").value("Typed by the doctor"))
                .andExpect(jsonPath("$.history").value("Said: Nina homa siku tatu."));
    }

    @Test
    void aSummaryBelongsToItsVisitAndOnlyItsDoctorAccepts() throws Exception {
        UUID visitId = consultation();
        String requestId = summarise(visitId).get("requestId").asString();
        UUID otherVisit = consultation();

        accept(doctor, otherVisit, requestId, List.of(0), null).andExpect(status().isNotFound());
        accept(ActingAs.staff(staffRepository, StaffRole.DOCTOR), visitId, requestId, List.of(0), null)
                .andExpect(status().isForbidden());
        accept(doctor, visitId, requestId, List.of(99), null).andExpect(status().isUnprocessableContent());
    }
}
