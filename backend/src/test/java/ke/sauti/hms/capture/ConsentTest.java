package ke.sauti.hms.capture;

import static ke.sauti.hms.support.ActingAs.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.common.error.BusinessRuleException;
import ke.sauti.hms.patient.PatientService;
import ke.sauti.hms.patient.PreferredLanguage;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/** No recording without a recorded, versioned consent decision. */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ConsentTest {

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
    private ConsentService consents;

    @Autowired
    private ApplicationEvents events;

    private Staff reception;

    @BeforeEach
    void setUp() {
        reception = ActingAs.staff(staffRepository, StaffRole.RECEPTIONIST);
    }

    private VisitService.Actor desk() {
        return new VisitService.Actor(reception.getId(), StaffRole.RECEPTIONIST);
    }

    private UUID openVisit(PreferredLanguage language) {
        UUID patientId = patients.register(new RegisterPatientRequest(
                        "Consent Patient",
                        LocalDate.of(1988, 4, 4),
                        Sex.MALE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        language,
                        null,
                        null))
                .getId();
        return visits.open(patientId, null, desk()).getId();
    }

    private ResultActions decide(UUID visitId, String decision) throws Exception {
        return mvc.perform(post("/api/visits/" + visitId + "/consents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(
                        Map.of("purpose", "RECORD_RECEPTION", "decision", decision, "givenBy", "PATIENT")))
                .with(as(reception)));
    }

    @Test
    void recordingWithoutConsentIsRefused() {
        UUID visitId = openVisit(PreferredLanguage.SWAHILI);

        assertThatThrownBy(() -> consents.requireAgreed(visitId, ConsentPurpose.RECORD_RECEPTION))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Recording needs the patient's consent first");
    }

    @Test
    void anAgreementAllowsRecordingForThatPurposeOnly() throws Exception {
        UUID visitId = openVisit(PreferredLanguage.SWAHILI);

        decide(visitId, "AGREED")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("AGREED"))
                .andExpect(jsonPath("$.wordingVersion").value("v1-sw"))
                .andExpect(jsonPath("$.recordedBy").value(reception.getId().toString()));

        assertThatCode(() -> consents.requireAgreed(visitId, ConsentPurpose.RECORD_RECEPTION))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> consents.requireAgreed(visitId, ConsentPurpose.RECORD_CONSULTATION))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void theWordingVersionFollowsThePatientsLanguage() throws Exception {
        decide(openVisit(PreferredLanguage.ENGLISH), "AGREED")
                .andExpect(jsonPath("$.wordingVersion").value("v1-en"));
    }

    @Test
    void aWithdrawalIsRecordedAndStopsFurtherRecording() throws Exception {
        UUID visitId = openVisit(PreferredLanguage.SWAHILI);
        decide(visitId, "AGREED");

        decide(visitId, "WITHDRAWN").andExpect(status().isCreated());

        assertThatThrownBy(() -> consents.requireAgreed(visitId, ConsentPurpose.RECORD_RECEPTION))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(events.stream(ConsentWithdrawnEvent.class))
                .contains(new ConsentWithdrawnEvent(visitId, ConsentPurpose.RECORD_RECEPTION));
        mvc.perform(get("/api/visits/" + visitId + "/consents").with(as(reception)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].decision").value("AGREED"))
                .andExpect(jsonPath("$[1].decision").value("WITHDRAWN"));
    }

    @Test
    void aRefusalDoesNotAllowRecording() throws Exception {
        UUID visitId = openVisit(PreferredLanguage.SWAHILI);

        decide(visitId, "REFUSED").andExpect(status().isCreated());

        assertThatThrownBy(() -> consents.requireAgreed(visitId, ConsentPurpose.RECORD_RECEPTION))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void thereIsNothingToWithdrawWithoutAnAgreement() throws Exception {
        decide(openVisit(PreferredLanguage.SWAHILI), "WITHDRAWN")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("There is no agreement to withdraw"));
    }

    @Test
    void onlyTheCareTeamRecordsConsent() throws Exception {
        UUID visitId = openVisit(PreferredLanguage.SWAHILI);
        Staff otherDoctor = ActingAs.staff(staffRepository, StaffRole.DOCTOR);

        mvc.perform(post("/api/visits/" + visitId + "/consents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(
                                Map.of("purpose", "RECORD_CONSULTATION", "decision", "AGREED", "givenBy", "PATIENT")))
                        .with(as(otherDoctor)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aClosedVisitTakesNoNewDecisions() throws Exception {
        UUID visitId = openVisit(PreferredLanguage.SWAHILI);
        visits.changeStatus(visitId, VisitStatus.CANCELLED, "Patient left", desk());

        decide(visitId, "AGREED")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("This visit is closed"));
    }

    @Test
    void theWordingIsServedInEachLanguageAndMarkedAsDemonstration() throws Exception {
        mvc.perform(get("/api/consent/wording").param("language", "SWAHILI").with(as(reception)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("v1-sw"))
                .andExpect(jsonPath("$.demonstration").value(true))
                .andExpect(jsonPath("$.text").value(startsWith("MANENO YA MAJARIBIO")));
        mvc.perform(get("/api/consent/wording").param("language", "ENGLISH").with(as(reception)))
                .andExpect(jsonPath("$.text").value(startsWith("DEMONSTRATION WORDING")));
    }
}
