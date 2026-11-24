package ke.sauti.hms.capture;

import static ke.sauti.hms.support.ActingAs.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/** Upload a recording piece by piece and watch transcript segments appear (fake transcriber). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PiecePipelineTest {

    private static final JsonMapper JSON = JsonMapper.shared();

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        SeaweedFs.register(registry, "pipeline-audio");
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
    private TranscriptSegmentRepository segments;

    @Autowired
    private TranscribeHandler handler;

    @Autowired
    private JdbcTemplate jdbc;

    private Staff reception;
    private Staff recordingDoctor;

    @BeforeEach
    void setUp() {
        jdbc.update("update job set status = 'CANCELLED' where status = 'QUEUED'");
        reception = ActingAs.staff(staffRepository, StaffRole.RECEPTIONIST);
        recordingDoctor = ActingAs.staff(staffRepository, StaffRole.DOCTOR);
    }

    private static VisitService.Actor actor(Staff staff) {
        return new VisitService.Actor(staff.getId(), staff.getRole());
    }

    /** A consultation recording the doctor started, with consent. */
    private UUID recording() {
        UUID patientId = patients.register(new RegisterPatientRequest(
                        "Pipeline Patient",
                        LocalDate.of(1991, 2, 14),
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
        visits.changeStatus(visitId, VisitStatus.WAITING_DOCTOR, null, actor(triageNurse));
        visits.take(visitId, actor(recordingDoctor));
        consents.record(
                visitId,
                ConsentPurpose.RECORD_CONSULTATION,
                ConsentDecision.AGREED,
                ConsentGivenBy.PATIENT,
                actor(recordingDoctor));
        return captures.start(visitId, ConsentPurpose.RECORD_CONSULTATION, "sw", actor(recordingDoctor))
                .getId();
    }

    private static byte[] audio(int sequence) {
        return ("fake webm piece " + sequence).getBytes(StandardCharsets.UTF_8);
    }

    private ResultActions upload(Staff who, UUID sessionId, int sequence, byte[] audio, String sha, String type)
            throws Exception {
        return mvc.perform(put("/api/captures/" + sessionId + "/pieces/" + sequence)
                .contentType(type)
                .header("X-Piece-Sha256", sha)
                .header("X-Piece-Duration-Ms", "30000")
                .header("X-Piece-Offset-Ms", String.valueOf(sequence * 30_000))
                .content(audio)
                .with(as(who)));
    }

    private ResultActions upload(UUID sessionId, int sequence) throws Exception {
        byte[] audio = audio(sequence);
        return upload(
                recordingDoctor, sessionId, sequence, audio, PieceService.sha256(audio), "audio/webm;codecs=opus");
    }

    @Test
    void uploadingAPieceProducesGradedSegmentsAtTheRightTimes() throws Exception {
        UUID sessionId = recording();

        upload(sessionId, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("STORED"));
        worker.runOnce();

        List<TranscriptSegment> written = segments.findBySessionIdOrderByOrderNoAsc(sessionId);
        assertThat(written)
                .extracting(s -> s.getText() + " | " + s.getConfidence())
                .containsExactly(
                        "Habari daktari, nimekuwa na homa tangu jana. | HIGH",
                        "Sina kikohozi. | REVIEW",
                        "Nimemeza paracetamol mara mbili. | REVIEW",
                        "Kichwa kinauma upande wa kushoto. | LOW",
                        "Nina kizunguzungu kidogo asubuhi. | REVIEW");
        assertThat(written.getFirst().getStartMs())
                .as("offset by the piece position")
                .isEqualTo(30_000);
        assertThat(written.getFirst().getAvgLogprob()).isEqualTo(-0.21);
        byte[] raw = jdbc.queryForObject(
                "select text from transcript_segment where id = ?",
                byte[].class,
                written.getFirst().getId());
        assertThat(new String(raw, StandardCharsets.ISO_8859_1)).doesNotContain("homa");
    }

    @Test
    void aPieceTaggedWithAFieldIsDraftedAfterTranscription() throws Exception {
        UUID sessionId = receptionRecording();
        byte[] audio = audio(1);

        mvc.perform(put("/api/captures/" + sessionId + "/pieces/1")
                        .contentType("audio/webm")
                        .header("X-Piece-Sha256", PieceService.sha256(audio))
                        .header("X-Piece-Duration-Ms", "30000")
                        .header("X-Piece-Offset-Ms", "30000")
                        .header("X-Piece-Field", "REASON_FOR_VISIT")
                        .content(audio)
                        .with(as(reception)))
                .andExpect(status().isCreated());
        worker.runOnce();
        assertThat(jdbc.queryForObject(
                        "select count(*) from job where type = 'DRAFT_FIELD' and status = 'QUEUED'", Long.class))
                .isPositive();

        worker.runOnce();
        List<String> drafts = jdbc.queryForList(
                "select field from field_draft d join audio_piece p on p.id = d.piece_id where p.session_id = ?",
                String.class,
                sessionId);
        assertThat(drafts).containsExactly("REASON_FOR_VISIT");
    }

    @Test
    void anUnknownFieldTagIsRefused() throws Exception {
        UUID sessionId = recording();
        byte[] audio = audio(1);

        mvc.perform(put("/api/captures/" + sessionId + "/pieces/1")
                        .contentType("audio/webm")
                        .header("X-Piece-Sha256", PieceService.sha256(audio))
                        .header("X-Piece-Duration-Ms", "30000")
                        .header("X-Piece-Offset-Ms", "30000")
                        .header("X-Piece-Field", "NATIONAL_ID")
                        .content(audio)
                        .with(as(recordingDoctor)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendingTheSamePieceAgainIsHarmless() throws Exception {
        UUID sessionId = recording();
        String first = JSON.readTree(upload(sessionId, 0)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asString();

        upload(sessionId, 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first));
        Long jobs = jdbc.queryForObject(
                "select count(*) from job where payload_id = ?", Long.class, UUID.fromString(first));
        assertThat(jobs).as("transcribed once").isEqualTo(1);
    }

    @Test
    void aDifferentPieceWithTheSameNumberIsAConflict() throws Exception {
        UUID sessionId = recording();
        upload(sessionId, 0);
        byte[] other = "something else".getBytes(StandardCharsets.UTF_8);

        upload(recordingDoctor, sessionId, 0, other, PieceService.sha256(other), "audio/webm")
                .andExpect(status().isConflict());
    }

    @Test
    void aBadChecksumIsRefused() throws Exception {
        UUID sessionId = recording();

        upload(
                        recordingDoctor,
                        sessionId,
                        0,
                        audio(0),
                        PieceService.sha256("tampered".getBytes(StandardCharsets.UTF_8)),
                        "audio/webm")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("The checksum does not match the audio; send the piece again"));
    }

    @Test
    void onlyAudioIsAccepted() throws Exception {
        UUID sessionId = recording();
        byte[] text = "not audio".getBytes(StandardCharsets.UTF_8);

        upload(recordingDoctor, sessionId, 0, text, PieceService.sha256(text), MediaType.TEXT_PLAIN_VALUE)
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void aPieceOverTwoMegabytesIsRefused() throws Exception {
        UUID sessionId = recording();
        byte[] big = new byte[PieceService.MAX_BYTES + 1];

        upload(recordingDoctor, sessionId, 0, big, PieceService.sha256(big), "audio/webm")
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void onlyThePersonRecordingCanSendPieces() throws Exception {
        UUID sessionId = recording();
        Staff other = ActingAs.staff(staffRepository, StaffRole.NURSE);
        byte[] audio = audio(0);

        upload(other, sessionId, 0, audio, PieceService.sha256(audio), "audio/webm")
                .andExpect(status().isForbidden());
    }

    /** A reception recording of the reason for visit, with consent. */
    private UUID receptionRecording() {
        UUID patientId = patients.register(new RegisterPatientRequest(
                        "Desk Patient",
                        LocalDate.of(1991, 2, 14),
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
        consents.record(
                visitId,
                ConsentPurpose.RECORD_RECEPTION,
                ConsentDecision.AGREED,
                ConsentGivenBy.PATIENT,
                actor(reception));
        return captures.start(visitId, ConsentPurpose.RECORD_RECEPTION, "sw", actor(reception))
                .getId();
    }

    private ResultActions uploadForField(Staff who, UUID sessionId, String field) throws Exception {
        byte[] audio = audio(0);
        var request = put("/api/captures/" + sessionId + "/pieces/0")
                .contentType("audio/webm")
                .header("X-Piece-Sha256", PieceService.sha256(audio))
                .header("X-Piece-Duration-Ms", "5000")
                .header("X-Piece-Offset-Ms", "0")
                .content(audio)
                .with(as(who));
        return mvc.perform(field == null ? request : request.header("X-Piece-Field", field));
    }

    @Test
    void receptionAudioMustBeForTheReasonField() throws Exception {
        UUID sessionId = receptionRecording();

        uploadForField(reception, sessionId, null).andExpect(status().isUnprocessableContent());
        uploadForField(reception, sessionId, "RESIDENCE").andExpect(status().isUnprocessableContent());
        uploadForField(reception, sessionId, "REASON_FOR_VISIT").andExpect(status().isCreated());
    }

    @Test
    void aConsultationPieceCannotFillAField() throws Exception {
        uploadForField(recordingDoctor, recording(), "REASON_FOR_VISIT").andExpect(status().isUnprocessableContent());
    }

    @Test
    void anErasedRecordingAnswersGone() throws Exception {
        UUID sessionId = recording();
        jdbc.update("update capture_session set status = 'ERASED' where id = ?", sessionId);

        upload(sessionId, 0)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("GONE"));
    }

    @Test
    void piecesAreRefusedOnceConsentIsWithdrawn() throws Exception {
        UUID sessionId = recording();
        UUID visitId = captures.get(sessionId, actor(recordingDoctor)).getVisitId();
        consents.record(
                visitId,
                ConsentPurpose.RECORD_CONSULTATION,
                ConsentDecision.WITHDRAWN,
                ConsentGivenBy.PATIENT,
                actor(recordingDoctor));

        upload(sessionId, 0).andExpect(status().isUnprocessableContent());
    }

    @Test
    void theRecordingCompletesWhenEveryExpectedPieceIsTranscribed() throws Exception {
        UUID sessionId = recording();
        upload(sessionId, 0);
        upload(sessionId, 1);
        mvc.perform(post("/api/captures/" + sessionId + "/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedPieces\":2}")
                        .with(as(recordingDoctor)))
                .andExpect(jsonPath("$.status").value("STOPPED"));

        worker.runOnce();

        assertThat(captures.get(sessionId, actor(recordingDoctor)).getStatus()).isEqualTo(CaptureStatus.COMPLETE);
        assertThat(segments.findBySessionIdOrderByOrderNoAsc(sessionId))
                .as("both pieces were transcribed")
                .isNotEmpty();
    }

    @Test
    void transcribingAPieceTwiceReplacesItsSegmentsInsteadOfAddingMore() throws Exception {
        UUID sessionId = recording();
        UUID pieceId = UUID.fromString(
                JSON.readTree(upload(sessionId, 0).andReturn().getResponse().getContentAsString())
                        .get("id")
                        .asString());
        worker.runOnce();
        int once = segments.findBySessionIdOrderByOrderNoAsc(sessionId).size();

        handler.handle(pieceId);
        jdbc.update("update audio_piece set status = 'STORED' where id = ?", pieceId);
        handler.handle(pieceId);

        assertThat(segments.findBySessionIdOrderByOrderNoAsc(sessionId)).hasSize(once);
    }
}
