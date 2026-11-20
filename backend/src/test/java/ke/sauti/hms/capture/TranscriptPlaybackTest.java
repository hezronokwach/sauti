package ke.sauti.hms.capture;

import static ke.sauti.hms.support.ActingAs.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.jobs.JobWorker;
import ke.sauti.hms.patient.PatientService;
import ke.sauti.hms.patient.Sex;
import ke.sauti.hms.patient.dto.RegisterPatientRequest;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.staff.StaffRepository;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.support.ActingAs;
import ke.sauti.hms.support.SeaweedFs;
import ke.sauti.hms.visit.VisitService;
import ke.sauti.hms.visit.VisitStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reading the transcript, playing the audio, and erasing a recording. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class TranscriptPlaybackTest {

    private static final JsonMapper JSON = JsonMapper.shared();

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        SeaweedFs.register(registry, "playback-audio");
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private PatientService patients;

    @Autowired
    private VisitService visits;

    @Autowired
    private ConsentService consents;

    @Autowired
    private CaptureService captures;

    @Autowired
    private JobWorker worker;

    @Autowired
    private AudioStore store;

    @Autowired
    private JdbcTemplate jdbc;

    private Staff reception;
    private Staff nurse;

    @BeforeEach
    void setUp() {
        jdbc.update("update job set status = 'CANCELLED' where status = 'QUEUED'");
        reception = ActingAs.staff(staffRepository, StaffRole.RECEPTIONIST);
        nurse = ActingAs.staff(staffRepository, StaffRole.NURSE);
    }

    private static VisitService.Actor actor(Staff staff) {
        return new VisitService.Actor(staff.getId(), staff.getRole());
    }

    private UUID recording() {
        UUID patientId = patients.register(new RegisterPatientRequest(
                        "Playback Patient",
                        LocalDate.of(1983, 9, 1),
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
        consents.record(
                visitId,
                ConsentPurpose.RECORD_CONSULTATION,
                ConsentDecision.AGREED,
                ConsentGivenBy.PATIENT,
                actor(nurse));
        return captures.start(visitId, ConsentPurpose.RECORD_CONSULTATION, "sw", actor(nurse))
                .getId();
    }

    private static byte[] audio(int sequence) {
        return ("fake ogg piece " + sequence).getBytes(StandardCharsets.UTF_8);
    }

    private ResultActions upload(UUID sessionId, int sequence) throws Exception {
        byte[] audio = audio(sequence);
        return mvc.perform(put("/api/captures/" + sessionId + "/pieces/" + sequence)
                .contentType("audio/ogg")
                .header("X-Piece-Sha256", PieceService.sha256(audio))
                .header("X-Piece-Duration-Ms", "30000")
                .header("X-Piece-Offset-Ms", String.valueOf(sequence * 30_000))
                .content(audio)
                .with(as(nurse)));
    }

    private List<JsonNode> segments(UUID sessionId, Integer after) throws Exception {
        var request = get("/api/captures/" + sessionId + "/segments").with(as(nurse));
        if (after != null) {
            request.param("after", String.valueOf(after));
        }
        return JSON.readTree(mvc.perform(request)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .valueStream()
                .toList();
    }

    private long accessLogRows(String what) {
        Long rows = jdbc.queryForObject(
                "select count(*) from access_log where actor_id = ? and what = ?", Long.class, nurse.getId(), what);
        return rows == null ? 0 : rows;
    }

    @Test
    void theTranscriptIsReadInOrderAndPolledFromTheLastPosition() throws Exception {
        UUID sessionId = recording();
        upload(sessionId, 0);
        upload(sessionId, 1);
        worker.runOnce();

        List<JsonNode> all = segments(sessionId, null);
        assertThat(all).isNotEmpty();
        assertThat(all.getFirst().get("text").asString()).isEqualTo("Habari daktari, nimekuwa na homa tangu jana.");
        int lastOfFirstPiece = all.stream()
                .mapToInt(node -> node.get("orderNo").asInt())
                .filter(order -> order < TranscribeHandler.ORDER_STEP)
                .max()
                .orElseThrow();

        List<JsonNode> rest = segments(sessionId, lastOfFirstPiece);
        assertThat(rest).allMatch(node -> node.get("orderNo").asInt() >= TranscribeHandler.ORDER_STEP);
        assertThat(segments(sessionId, all.getLast().get("orderNo").asInt())).isEmpty();
        assertThat(accessLogRows("TRANSCRIPT_OPENED"))
                .as("only the first open is logged")
                .isEqualTo(1);
    }

    @Test
    void someoneNotLookingAfterThePatientCannotReadOrPlay() throws Exception {
        UUID sessionId = recording();
        Staff doctor = ActingAs.staff(staffRepository, StaffRole.DOCTOR);

        mvc.perform(get("/api/captures/" + sessionId + "/segments").with(as(doctor)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/captures/" + sessionId + "/audio")
                        .param("atMs", "0")
                        .with(as(doctor)))
                .andExpect(status().isForbidden());
    }

    @Test
    void playingFindsThePieceAtThatMomentDecryptedAndUncached() throws Exception {
        UUID sessionId = recording();
        upload(sessionId, 0);
        upload(sessionId, 1);

        mvc.perform(get("/api/captures/" + sessionId + "/audio")
                        .param("atMs", "31000")
                        .with(as(nurse)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/ogg"))
                .andExpect(content().bytes(audio(1)))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Piece-Offset-Ms", "30000"));
        mvc.perform(get("/api/captures/" + sessionId + "/audio")
                        .param("atMs", "90000")
                        .with(as(nurse)))
                .andExpect(status().isNotFound());

        assertThat(accessLogRows("AUDIO_PLAYED"))
                .as("the failed play (404) is not logged")
                .isEqualTo(1);
    }

    @Test
    void erasingRemovesTheAudioAndTranscriptAndRefusesFurtherPieces() throws Exception {
        UUID sessionId = recording();
        upload(sessionId, 0);
        worker.runOnce();
        upload(sessionId, 1);
        String key = jdbc.queryForObject(
                "select storage_key from audio_piece where session_id = ? and sequence_no = 0",
                String.class,
                sessionId);
        UUID queuedPiece = jdbc.queryForObject(
                "select id from audio_piece where session_id = ? and sequence_no = 1", UUID.class, sessionId);

        mvc.perform(post("/api/captures/" + sessionId + "/erase").with(as(nurse)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ERASED"));

        assertThat(jdbc.queryForObject(
                        "select count(*) from transcript_segment where session_id = ?", Long.class, sessionId))
                .isZero();
        assertThat(jdbc.queryForList(
                        "select distinct status from audio_piece where session_id = ?", String.class, sessionId))
                .containsExactly("ERASED");
        assertThat(jdbc.queryForObject("select status from job where payload_id = ?", String.class, queuedPiece))
                .isEqualTo("CANCELLED");
        assertThatThrownBy(() -> store.get(key)).isInstanceOf(NoSuchKeyException.class);
        upload(sessionId, 2).andExpect(status().isGone());
        mvc.perform(get("/api/captures/" + sessionId + "/segments").with(as(nurse)))
                .andExpect(status().isGone());
        String detail = jdbc.queryForObject(
                "select detail::text from audit_event where action = 'CAPTURE_ERASED' and entity_id = ?",
                String.class,
                sessionId);
        assertThat(detail).contains("\"pieces\": 2").doesNotContain("homa");
    }
}
