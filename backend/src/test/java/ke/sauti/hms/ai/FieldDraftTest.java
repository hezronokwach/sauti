package ke.sauti.hms.ai;

import static ke.sauti.hms.support.ActingAs.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.capture.ConsentDecision;
import ke.sauti.hms.capture.ConsentGivenBy;
import ke.sauti.hms.capture.ConsentPurpose;
import ke.sauti.hms.capture.ConsentService;
import ke.sauti.hms.capture.DraftField;
import ke.sauti.hms.common.crypto.ColumnCipher;
import ke.sauti.hms.patient.PatientService;
import ke.sauti.hms.patient.Sex;
import ke.sauti.hms.patient.dto.RegisterPatientRequest;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** Voice drafts: kept only when the quote was said, stored apart, confirmed through the normal form. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class FieldDraftTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private PatientService patients;

    @Autowired
    private VisitService visits;

    @Autowired
    private ConsentService consent;

    @Autowired
    private ColumnCipher cipher;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DraftFieldHandler drafting;

    @MockitoBean
    private ClinicalAi ai;

    private Staff reception;
    private Staff nurse;
    private UUID patientId;
    private UUID visitId;

    @BeforeEach
    void setUp() {
        reception = ActingAs.staff(staffRepository, StaffRole.RECEPTIONIST);
        nurse = ActingAs.staff(staffRepository, StaffRole.NURSE);
        patientId = patients.register(new RegisterPatientRequest(
                        "Draft Patient",
                        LocalDate.of(1980, 6, 1),
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
        visitId = visits.open(patientId, null, actor(reception)).getId();
        consent.record(
                visitId,
                ConsentPurpose.RECORD_RECEPTION,
                ConsentDecision.AGREED,
                ConsentGivenBy.PATIENT,
                actor(reception));
        when(ai.model()).thenReturn("test-model");
        when(ai.fieldPromptVersion()).thenReturn("field-draft-v1");
    }

    private static VisitService.Actor actor(Staff staff) {
        return new VisitService.Actor(staff.getId(), staff.getRole());
    }

    /** A reception recording piece tagged with this field, holding this line. */
    private UUID taggedPiece(DraftField field, String line) {
        UUID sessionId = UUID.randomUUID();
        UUID pieceId = UUID.randomUUID();
        jdbc.update(
                "insert into capture_session (id, visit_id, purpose, started_by, status, started_at)"
                        + " values (?, ?, 'RECORD_RECEPTION', ?, 'RECORDING', now())",
                sessionId,
                visitId,
                reception.getId());
        jdbc.update(
                "insert into audio_piece (id, session_id, sequence_no, storage_key, duration_ms, offset_ms, sha256,"
                        + " status, field_tag) values (?, ?, 0, 'test', 8000, 2000, ?, 'TRANSCRIBED', ?)",
                pieceId,
                sessionId,
                "0".repeat(64),
                field.name());
        jdbc.update(
                "insert into transcript_segment (piece_id, session_id, order_no, start_ms, end_ms, text, confidence)"
                        + " values (?, ?, 0, 2500, 6000, ?, 'HIGH')",
                pieceId,
                sessionId,
                cipher.encrypt(line.getBytes(StandardCharsets.UTF_8)));
        return pieceId;
    }

    private long drafts() {
        Long n = jdbc.queryForObject("select count(*) from field_draft where visit_id = ?", Long.class, visitId);
        return n == null ? 0 : n;
    }

    @Test
    void aDraftWhoseQuoteWasSaidIsKeptWithWhereToPlayIt() throws Exception {
        UUID piece = taggedPiece(DraftField.RESIDENCE, "Naishi Kibera hapa Nairobi.");
        when(ai.draftField(any(), anyString()))
                .thenReturn(Optional.of(new ClinicalAi.FieldProposal("Kibera, Nairobi", "naishi Kibera hapa Nairobi")));

        drafting.handle(piece);
        drafting.handle(piece);

        mvc.perform(get("/api/visits/" + visitId + "/field-drafts").with(as(reception)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].field").value("RESIDENCE"))
                .andExpect(jsonPath("$[0].value").value("Kibera, Nairobi"))
                .andExpect(jsonPath("$[0].quote").value("naishi Kibera hapa Nairobi"))
                .andExpect(jsonPath("$[0].startMs").value(2500));
        assertThat(jdbc.queryForObject(
                        "select count(*) from ai_request where kind = 'FIELD_DRAFT' and visit_id = ?",
                        Long.class,
                        visitId))
                .isEqualTo(2);
    }

    @Test
    void aDraftWhoseQuoteIsNotInTheTranscriptIsDropped() {
        UUID piece = taggedPiece(DraftField.NEXT_OF_KIN_NAME, "Ndugu yangu anaitwa Otieno.");
        when(ai.draftField(any(), anyString()))
                .thenReturn(
                        Optional.of(new ClinicalAi.FieldProposal("Peter Otieno Ouma", "anaitwa Peter Otieno Ouma")));

        drafting.handle(piece);

        assertThat(drafts()).isZero();
        assertThat(jdbc.queryForObject(
                        "select error from ai_request where kind = 'FIELD_DRAFT' and visit_id = ?",
                        String.class,
                        visitId))
                .isEqualTo("Dropped: the quote is not in the transcript");
    }

    @Test
    void nothingSaidMeansNoDraft() {
        UUID piece = taggedPiece(DraftField.REASON_FOR_VISIT, "Mm.");
        when(ai.draftField(any(), anyString())).thenReturn(Optional.empty());

        drafting.handle(piece);

        assertThat(drafts()).isZero();
    }

    @Test
    void draftsNeverReachThePatientRecordUntilSavedThroughTheForm() throws Exception {
        UUID piece = taggedPiece(DraftField.RESIDENCE, "Naishi Kibera hapa Nairobi.");
        when(ai.draftField(any(), anyString()))
                .thenReturn(Optional.of(new ClinicalAi.FieldProposal("Kibera, Nairobi", "naishi Kibera hapa Nairobi")));
        drafting.handle(piece);
        String draftId = jdbc.queryForObject("select id from field_draft where visit_id = ?", String.class, visitId);

        assertThat(patients.get(patientId).getResidence()).isNull();

        mvc.perform(put("/api/patients/" + patientId + "/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"residence\":\"Kibera, Nairobi\",\"nextOfKinName\":\"\"}")
                        .with(as(reception)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.residence").value("Kibera, Nairobi"));
        mvc.perform(post("/api/visits/" + visitId + "/field-drafts/" + draftId + "/confirm")
                        .with(as(reception)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(post("/api/visits/" + visitId + "/field-drafts/" + draftId + "/dismiss")
                        .with(as(reception)))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/visits/" + visitId + "/field-drafts").with(as(reception)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void theTriageNurseConfirmsTheReasonAndAChangeNeedsConfirmingAgain() throws Exception {
        String reason = JsonMapper.shared().writeValueAsString(java.util.Map.of("reasonForVisit", "Homa siku tatu"));
        mvc.perform(put("/api/visits/" + visitId + "/reason")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reason)
                        .with(as(reception)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed").value(false));
        visits.changeStatus(visitId, VisitStatus.WAITING_TRIAGE, null, actor(reception));
        visits.take(visitId, actor(nurse));

        mvc.perform(post("/api/visits/" + visitId + "/reason/confirm").with(as(reception)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/visits/" + visitId + "/reason/confirm").with(as(nurse)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed").value(true))
                .andExpect(jsonPath("$.reasonForVisit").value("Homa siku tatu"));

        mvc.perform(put("/api/visits/" + visitId + "/reason")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonMapper.shared()
                                .writeValueAsString(java.util.Map.of("reasonForVisit", "Homa na kikohozi")))
                        .with(as(nurse)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed").value(false));
    }
}
