package ke.sauti.hms.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.cleanup.NightlyCleanup;
import ke.sauti.hms.note.NoteService;
import ke.sauti.hms.note.dto.SaveNoteRequest;
import ke.sauti.hms.patient.PatientService;
import ke.sauti.hms.patient.Sex;
import ke.sauti.hms.patient.dto.RegisterPatientRequest;
import ke.sauti.hms.prereg.PreRegistrationService;
import ke.sauti.hms.prereg.dto.PreRegisterRequest;
import ke.sauti.hms.security.CodeVerifier;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.staff.StaffRepository;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.support.ActingAs;
import ke.sauti.hms.support.SeaweedFs;
import ke.sauti.hms.visit.VisitService;
import ke.sauti.hms.visit.VisitStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/** The clean-up removes old audio and leftovers, and never a clinical record. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class NightlyCleanupTest {

    private static final String GOOD_CODE = "123456";
    private static final List<String> KEPT_TABLES =
            List.of("clinical_note", "note_addendum", "prescription", "prescription_item", "vitals");

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        SeaweedFs.register(registry, "cleanup-audio");
    }

    @Autowired
    private NightlyCleanup cleanup;

    @Autowired
    private AudioRetention retention;

    @Autowired
    private AudioStore store;

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
    private PieceService pieces;

    @Autowired
    private NoteService notes;

    @Autowired
    private PreRegistrationService preRegistrations;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private CodeVerifier verifier;

    private Staff reception;
    private Staff nurse;
    private Staff doctor;

    /** A stopped recording with one stored piece and one transcript line. */
    private record Recording(UUID visitId, UUID sessionId, UUID pieceId, String key) {}

    @BeforeEach
    void people() {
        Mockito.when(verifier.verify(ArgumentMatchers.any(), ArgumentMatchers.eq(GOOD_CODE)))
                .thenReturn(true);
        reception = ActingAs.staff(staffRepository, StaffRole.RECEPTIONIST);
        nurse = ActingAs.staff(staffRepository, StaffRole.NURSE);
        doctor = ActingAs.staff(staffRepository, StaffRole.DOCTOR);
        doctor.startAuthenticatorSetup("JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP");
        doctor.confirmAuthenticator();
        doctor = staffRepository.save(doctor);
    }

    private static VisitService.Actor actor(Staff staff) {
        return new VisitService.Actor(staff.getId(), staff.getRole());
    }

    private Recording recording() {
        UUID patientId = patients.register(new RegisterPatientRequest(
                        "Cleanup Patient",
                        LocalDate.of(1982, 6, 6),
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
        UUID sessionId = captures.start(visitId, ConsentPurpose.RECORD_CONSULTATION, "sw", actor(nurse))
                .getId();
        byte[] audio = ("audio " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        AudioPiece piece = pieces.receive(
                        sessionId,
                        new PieceService.Upload(
                                0, "audio/webm;codecs=opus", PieceService.sha256(audio), 30_000, 0, null),
                        audio,
                        actor(nurse))
                .piece();
        captures.stop(sessionId, 1, actor(nurse));
        jdbc.update(
                "insert into transcript_segment (piece_id, session_id, order_no, start_ms, end_ms, text, confidence)"
                        + " values (?, ?, 0, 0, 1000, ?, 'HIGH')",
                piece.getId(),
                sessionId,
                new byte[] {1, 2, 3});
        visits.changeStatus(visitId, VisitStatus.WAITING_DOCTOR, null, actor(nurse));
        visits.take(visitId, actor(doctor));
        return new Recording(visitId, sessionId, piece.getId(), piece.getStorageKey());
    }

    private void signNote(Recording recording, Instant signedAt) {
        long version = notes.saveDraft(
                        recording.visitId(),
                        new SaveNoteRequest(null, "Cough", null, null, "Cold", "Rest", false),
                        actor(doctor))
                .note()
                .getVersion();
        notes.sign(recording.visitId(), version, GOOD_CODE, actor(doctor));
        jdbc.update(
                "update clinical_note set signed_at = ? where visit_id = ?",
                Timestamp.from(signedAt),
                recording.visitId());
    }

    private String pieceStatus(Recording recording) {
        return jdbc.queryForObject("select status from audio_piece where id = ?", String.class, recording.pieceId());
    }

    private boolean fileExists(String key) {
        try {
            store.getRaw(key);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    private String fingerprint(String table) {
        return jdbc.queryForObject(
                "select md5(coalesce(string_agg(t::text, ',' order by t::text), '')) from " + table + " t",
                String.class);
    }

    private List<String> auditRows() {
        return jdbc.queryForList("select a::text from audit_event a", String.class);
    }

    private static Instant daysAgo(long days) {
        return Instant.now().minus(Duration.ofDays(days));
    }

    @Test
    void oldAudioGoesAndEveryRecordStays() {
        Recording old = recording();
        signNote(old, daysAgo(31));
        List<String> before = KEPT_TABLES.stream().map(this::fingerprint).toList();
        List<String> auditBefore = auditRows();

        cleanup.run();

        assertThat(pieceStatus(old)).isEqualTo("ERASED");
        assertThat(fileExists(old.key())).isFalse();
        assertThat(jdbc.queryForObject(
                        "select count(*) from transcript_segment where session_id = ?", Integer.class, old.sessionId()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select status from capture_session where id = ?", String.class, old.sessionId()))
                .isNotEqualTo("ERASED");
        assertThat(KEPT_TABLES.stream().map(this::fingerprint).toList()).isEqualTo(before);
        assertThat(auditRows()).as("existing audit rows are unchanged").containsAll(auditBefore);
        String detail = jdbc.queryForObject(
                "select detail::text from audit_event where action = 'AUDIO_RETENTION_ERASED' and entity_id = ?",
                String.class,
                old.sessionId());
        assertThat(detail).contains("pieces");
    }

    @Test
    void recentAndUnsignedAudioStays() {
        Recording recent = recording();
        signNote(recent, daysAgo(29));
        Recording unsigned = recording();

        cleanup.run();

        assertThat(pieceStatus(recent)).isNotEqualTo("ERASED");
        assertThat(fileExists(recent.key())).isTrue();
        assertThat(pieceStatus(unsigned)).isNotEqualTo("ERASED");
        assertThat(fileExists(unsigned.key())).isTrue();
    }

    @Test
    void onlyOldRecordingFilesWithNoLiveRowAreOrphans() {
        Recording live = recording();
        String orphan = "sessions/" + UUID.randomUUID() + "/000000";
        String spokenInstruction = "instructions/" + UUID.randomUUID();
        store.put(orphan, new byte[] {1});
        store.put(spokenInstruction, new byte[] {2});

        retention.deleteOrphansOlderThan(Instant.now().minus(Duration.ofHours(1)));
        assertThat(fileExists(orphan))
                .as("a new file may be an upload in progress")
                .isTrue();

        retention.deleteOrphansOlderThan(Instant.now().plus(Duration.ofHours(1)));

        assertThat(fileExists(orphan)).isFalse();
        assertThat(fileExists(live.key())).isTrue();
        assertThat(fileExists(spokenInstruction)).isTrue();
    }

    @Test
    void onlyPreRegistrationsFromEarlierDaysAreDeleted() {
        String oldCode = preRegistrations.submit(preRegistration("0711000001"), "10.9.0.1");
        String todayCode = preRegistrations.submit(preRegistration("0711000002"), "10.9.0.1");
        jdbc.update(
                "update pre_registration set ticket_date = ? where ticket_id = (select id from queue_ticket"
                        + " where code = ? order by created_at desc limit 1)",
                Date.valueOf(LocalDate.of(2020, 1, 1)),
                oldCode);

        preRegistrations.deleteFromEarlierDays();

        assertThat(count(oldCode)).isZero();
        assertThat(count(todayCode)).isEqualTo(1);
    }

    private static PreRegisterRequest preRegistration(String phone) {
        return new PreRegisterRequest("Cleanup Arrival", LocalDate.of(1990, 1, 1), Sex.FEMALE, phone, null, null);
    }

    private Integer count(String code) {
        return jdbc.queryForObject(
                "select count(*) from pre_registration p join queue_ticket t on t.id = p.ticket_id where t.code = ?"
                        + " and t.created_at > now() - interval '1 hour'",
                Integer.class,
                code);
    }

    @Test
    void aMissingFileDoesNotStopTheCleanUp() {
        assertThatThrownBy(() -> store.getRaw("sessions/missing")).isInstanceOf(NoSuchKeyException.class);
        cleanup.run();
    }
}
