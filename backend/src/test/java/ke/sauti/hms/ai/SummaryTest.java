package ke.sauti.hms.ai;

import static ke.sauti.hms.support.ActingAs.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Summaries: access and consent first, the model outside any transaction, every claim checked, every call recorded. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SummaryTest {

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

    @MockitoBean
    private ClinicalAi ai;

    private Staff nurse;
    private Staff doctor;
    private UUID visitId;

    @BeforeEach
    void setUp() {
        Staff reception = ActingAs.staff(staffRepository, StaffRole.RECEPTIONIST);
        nurse = ActingAs.staff(staffRepository, StaffRole.NURSE);
        doctor = ActingAs.staff(staffRepository, StaffRole.DOCTOR);
        UUID patientId = patients.register(new RegisterPatientRequest(
                        "Summary Patient",
                        LocalDate.of(1990, 7, 1),
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
        visitId = visits.open(patientId, null, actor(reception)).getId();
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
        when(ai.model()).thenReturn("test-model");
        when(ai.promptVersion()).thenReturn("summary-v1");
    }

    private static VisitService.Actor actor(Staff staff) {
        return new VisitService.Actor(staff.getId(), staff.getRole());
    }

    /** A consultation recording with these lines, one HIGH and one REVIEW. */
    private void transcript(String... lines) {
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
        for (int i = 0; i < lines.length; i++) {
            jdbc.update(
                    "insert into transcript_segment (piece_id, session_id, order_no, start_ms, end_ms, text, confidence)"
                            + " values (?, ?, ?, ?, ?, ?, ?)",
                    pieceId,
                    sessionId,
                    i,
                    i * 4_000,
                    i * 4_000 + 3_000,
                    cipher.encrypt(lines[i].getBytes(StandardCharsets.UTF_8)),
                    i == 1 ? "REVIEW" : "HIGH");
        }
    }

    private ResultActions summarise(Staff who) throws Exception {
        return mvc.perform(post("/api/visits/" + visitId + "/summary").with(as(who)));
    }

    private long requests(String status) {
        Long n = jdbc.queryForObject(
                "select count(*) from ai_request where visit_id = ? and status = ?", Long.class, visitId, status);
        return n == null ? 0 : n;
    }

    @Test
    void everyClaimIsCheckedAndThePlantedFeverIsCaught() throws Exception {
        transcript("Habari daktari, nina homa siku tatu.", "Sina homa usiku, ila kichwa kinauma.");
        when(ai.summarise(anyString()))
                .thenReturn(List.of(
                        new ClinicalAi.DraftClaim("complaint", "Fever for three days", "nina homa siku tatu"),
                        new ClinicalAi.DraftClaim("history", "Has fever at night", "Sina homa usiku"),
                        new ClinicalAi.DraftClaim("plan", "Amoxicillin 500 mg", "nitakupa amoxicillin")));

        summarise(doctor)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.claims[0].supported").value(true))
                .andExpect(jsonPath("$.claims[0].checkAudio").value(false))
                .andExpect(jsonPath("$.claims[1].supported").value(false))
                .andExpect(jsonPath("$.claims[1].negationConsistent").value(false))
                .andExpect(jsonPath("$.claims[1].checkAudio").value(true))
                .andExpect(jsonPath("$.claims[1].startMs").value(4_000))
                .andExpect(jsonPath("$.claims[2].quotePresent").value(false));

        assertThat(requests("OK")).isEqualTo(1);
        byte[] stored = jdbc.queryForObject("select claims from ai_request where visit_id = ?", byte[].class, visitId);
        assertThat(stored).isNotNull();
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("homa");
        assertThat(jdbc.queryForObject(
                        "select model || ' ' || prompt_version from ai_request where visit_id = ?",
                        String.class,
                        visitId))
                .isEqualTo("test-model summary-v1");
    }

    @Test
    void aFailedCallIsRetriedOnce() throws Exception {
        transcript("Nina homa siku tatu.");
        when(ai.summarise(anyString()))
                .thenThrow(new IllegalStateException("timeout"))
                .thenReturn(List.of(new ClinicalAi.DraftClaim("complaint", "Fever", "nina homa")));

        summarise(doctor)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));

        verify(ai, times(2)).summarise(anyString());
        assertThat(requests("OK")).isEqualTo(1);
    }

    @Test
    void afterTwoFailuresTheSummaryIsUnavailableAndStillRecorded() throws Exception {
        transcript("Nina homa siku tatu.");
        when(ai.summarise(anyString())).thenThrow(new IllegalStateException("timeout"));

        summarise(doctor)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(
                        jsonPath("$.message").value("Summary unavailable; write the note yourself or try again later"))
                .andExpect(jsonPath("$.claims.length()").value(0));

        verify(ai, times(2)).summarise(anyString());
        assertThat(requests("UNAVAILABLE")).isEqualTo(1);
    }

    @Test
    void withdrawnConsentMeansNoSummaryAndNoCall() throws Exception {
        transcript("Nina homa siku tatu.");
        consent.record(
                visitId,
                ConsentPurpose.RECORD_CONSULTATION,
                ConsentDecision.WITHDRAWN,
                ConsentGivenBy.PATIENT,
                actor(doctor));

        summarise(doctor).andExpect(status().isUnprocessableContent());

        verify(ai, never()).summarise(anyString());
    }

    @Test
    void onlyTheDoctorOnTheCareTeamMayAsk() throws Exception {
        transcript("Nina homa siku tatu.");

        summarise(ActingAs.staff(staffRepository, StaffRole.DOCTOR)).andExpect(status().isForbidden());
        summarise(nurse).andExpect(status().isForbidden());

        verify(ai, never()).summarise(anyString());
    }

    @Test
    void thereMustBeATranscript() throws Exception {
        summarise(doctor)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("There is no transcript to summarise yet"));
    }
}
