package ke.sauti.hms.medication;

import static ke.sauti.hms.support.ActingAs.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

/** The medicine list and draft prescriptions: who may write, and no lost changes. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PrescriptionTest {

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
    private JdbcTemplate jdbc;

    private Staff reception;
    private Staff nurse;
    private Staff doctor;
    private Staff cashier;
    private UUID patientId;
    private UUID amoxicillin;
    private UUID paracetamol;

    @BeforeEach
    void setUp() {
        reception = ActingAs.staff(staffRepository, StaffRole.RECEPTIONIST);
        nurse = ActingAs.staff(staffRepository, StaffRole.NURSE);
        doctor = ActingAs.staff(staffRepository, StaffRole.DOCTOR);
        cashier = ActingAs.staff(staffRepository, StaffRole.CASHIER);
        patientId = patients.register(new RegisterPatientRequest(
                        "Prescription Patient",
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
        amoxicillin = medicine("AMOX-500-CAP");
        paracetamol = medicine("PARA-500-TAB");
    }

    private UUID medicine(String code) {
        return jdbc.queryForObject("select id from medicine where code = ?", UUID.class, code);
    }

    private static VisitService.Actor actor(Staff staff) {
        return new VisitService.Actor(staff.getId(), staff.getRole());
    }

    private UUID visitWith(Staff withDoctor) {
        UUID visitId = visits.open(patientId, null, actor(reception)).getId();
        visits.changeStatus(visitId, VisitStatus.WAITING_TRIAGE, null, actor(reception));
        visits.take(visitId, actor(nurse));
        visits.changeStatus(visitId, VisitStatus.WAITING_DOCTOR, null, actor(nurse));
        visits.take(visitId, actor(withDoctor));
        return visitId;
    }

    private Map<String, Object> item(Integer revision, UUID medicineId) {
        Map<String, Object> body = new HashMap<>();
        body.put("revision", revision);
        body.put("medicineId", medicineId);
        body.put("doseValue", 500);
        body.put("doseUnit", "MG");
        body.put("frequency", "TDS");
        body.put("durationDays", 5);
        body.put("route", "ORAL");
        body.put("quantity", 15);
        body.put("instructions", "Baada ya chakula");
        return body;
    }

    private ResultActions add(Staff who, UUID visitId, Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/visits/" + visitId + "/prescription/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body))
                .with(as(who)));
    }

    private static String text(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static int revision(ResultActions result) throws Exception {
        return JSON.readTree(text(result)).get("revision").asInt();
    }

    private static String itemId(ResultActions result, int index) throws Exception {
        return JSON.readTree(text(result)).get("items").get(index).get("id").asString();
    }

    @Test
    void theListIsLoadedAndSearchFindsByPartOfTheName() throws Exception {
        assertThat(jdbc.queryForObject("select count(*) from medicine", Integer.class))
                .isGreaterThanOrEqualTo(50);

        mvc.perform(get("/api/medicines").param("q", "AMOX").with(as(doctor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Amoxicillin"))
                .andExpect(jsonPath("$[0].routes[0]").value("ORAL"));
        mvc.perform(get("/api/medicines").param("q", "a").with(as(doctor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/medicines").param("q", "%%").with(as(doctor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void theDoctorAddsEditsAndRemovesItemsAndTheRulesVersionIsStored() throws Exception {
        UUID visitId = visitWith(doctor);

        ResultActions added = add(doctor, visitId, item(null, amoxicillin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.rulesVersion").value("medicines-v1"))
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.items[0].medicine.name").value("Amoxicillin"))
                .andExpect(jsonPath("$.items[0].instructions").value("Baada ya chakula"));
        String itemId = itemId(added, 0);

        Map<String, Object> edited = item(1, amoxicillin);
        edited.put("quantity", 21);
        ResultActions saved = mvc.perform(put("/api/visits/" + visitId + "/prescription/items/" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(edited))
                        .with(as(doctor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(21));

        mvc.perform(delete("/api/visits/" + visitId + "/prescription/items/" + itemId)
                        .param("revision", String.valueOf(revision(saved)))
                        .with(as(doctor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void aDraftMayHaveMissingDetailsUntilItIsSigned() throws Exception {
        UUID visitId = visitWith(doctor);
        Map<String, Object> onlyMedicine = new HashMap<>();
        onlyMedicine.put("medicineId", paracetamol);

        add(doctor, visitId, onlyMedicine)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].doseValue").isEmpty());
    }

    @Test
    void aStaleScreenCannotOverwriteANewerChange() throws Exception {
        UUID visitId = visitWith(doctor);
        add(doctor, visitId, item(null, amoxicillin)).andExpect(status().isOk());

        add(doctor, visitId, item(0, paracetamol)).andExpect(status().isConflict());
        add(doctor, visitId, item(null, paracetamol)).andExpect(status().isConflict());
        add(doctor, visitId, item(1, paracetamol)).andExpect(status().isOk());
    }

    @Test
    void instructionsAreEncryptedInTheDatabase() throws Exception {
        UUID visitId = visitWith(doctor);
        String itemId = itemId(add(doctor, visitId, item(null, amoxicillin)), 0);

        byte[] stored = jdbc.queryForObject(
                "select instructions from prescription_item where id = ?", byte[].class, UUID.fromString(itemId));

        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("chakula");
    }

    @Test
    void aRouteTheMedicineCannotTakeOrAnUnknownMedicineIsRefused() throws Exception {
        UUID visitId = visitWith(doctor);
        Map<String, Object> wrongRoute = item(null, amoxicillin);
        wrongRoute.put("route", "IV");

        add(doctor, visitId, wrongRoute).andExpect(status().isUnprocessableContent());
        add(doctor, visitId, item(null, UUID.randomUUID())).andExpect(status().isUnprocessableContent());
    }

    @Test
    void badNumbersAreRefused() throws Exception {
        UUID visitId = visitWith(doctor);
        for (Map.Entry<String, Object> bad : Map.<String, Object>of("doseValue", 0, "durationDays", 366, "quantity", 0)
                .entrySet()) {
            Map<String, Object> body = item(null, amoxicillin);
            body.put(bad.getKey(), bad.getValue());
            add(doctor, visitId, body).andExpect(status().isBadRequest());
        }
    }

    @Test
    void anotherDoctorIsRefusedAndTheCareTeamCanRead() throws Exception {
        UUID visitId = visitWith(doctor);
        Staff otherDoctor = ActingAs.staff(staffRepository, StaffRole.DOCTOR);

        add(otherDoctor, visitId, item(null, amoxicillin)).andExpect(status().isForbidden());
        add(doctor, visitId, item(null, amoxicillin)).andExpect(status().isOk());

        mvc.perform(get("/api/visits/" + visitId + "/prescription").with(as(otherDoctor)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/visits/" + visitId + "/prescription").with(as(nurse)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void itCannotChangeOnceThePatientHasLeftTheDoctor() throws Exception {
        UUID visitId = visitWith(doctor);
        add(doctor, visitId, item(null, amoxicillin)).andExpect(status().isOk());
        visits.changeStatus(visitId, VisitStatus.WAITING_PAYMENT, null, actor(doctor));

        add(doctor, visitId, item(1, paracetamol)).andExpect(status().isUnprocessableContent());
    }

    @Test
    void anItemFromAnotherVisitCannotBeChanged() throws Exception {
        UUID firstVisit = visitWith(doctor);
        String firstItem = itemId(add(doctor, firstVisit, item(null, amoxicillin)), 0);
        visits.changeStatus(firstVisit, VisitStatus.WAITING_PAYMENT, null, actor(doctor));
        visits.changeStatus(firstVisit, VisitStatus.DONE, null, actor(cashier));
        UUID secondVisit = visitWith(doctor);
        add(doctor, secondVisit, item(null, paracetamol)).andExpect(status().isOk());

        mvc.perform(delete("/api/visits/" + secondVisit + "/prescription/items/" + firstItem)
                        .param("revision", "1")
                        .with(as(doctor)))
                .andExpect(status().isNotFound());
    }
}
