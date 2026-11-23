package ke.sauti.hms.security;

import static ke.sauti.hms.support.ActingAs.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.patient.PatientService;
import ke.sauti.hms.patient.Sex;
import ke.sauti.hms.patient.dto.RegisterPatientRequest;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.staff.StaffRepository;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.support.ActingAs;
import ke.sauti.hms.visit.VisitService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Logs carry ids only, and an anonymous caller does not get a database session. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class LogPrivacyTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private PatientService patients;

    @Autowired
    private VisitService visits;

    @Test
    void patientDetailsNeverReachTheLogs(CapturedOutput output) throws Exception {
        String marker = "Zzlogmarker" + UUID.randomUUID().toString().replace("-", "");
        Staff reception = ActingAs.staff(staffRepository, StaffRole.RECEPTIONIST);
        UUID patientId = patients.register(new RegisterPatientRequest(
                        marker,
                        LocalDate.of(1975, 5, 5),
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
        UUID visitId = visits.open(
                        patientId, "Reason " + marker, new VisitService.Actor(reception.getId(), reception.getRole()))
                .getId();

        mvc.perform(get("/api/patients/" + patientId).with(as(reception))).andExpect(status().isOk());
        mvc.perform(get("/api/visits/" + visitId + "/reason").with(as(reception)))
                .andExpect(status().isOk());
        // A refused request is the likeliest place for a body to be logged.
        mvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"" + marker + "\",\"dateOfBirth\":\"not-a-date\"}")
                        .with(as(reception)))
                .andExpect(status().isBadRequest());

        assertThat(output.getAll()).doesNotContain(marker);
    }

    @Test
    void anAnonymousCallerGetsNoSession() throws Exception {
        var refused = mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(refused.getRequest().getSession(false)).isNull();
    }
}
