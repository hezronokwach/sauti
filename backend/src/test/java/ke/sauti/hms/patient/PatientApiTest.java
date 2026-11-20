package ke.sauti.hms.patient;

import static ke.sauti.hms.support.ActingAs.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.staff.StaffRepository;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.support.ActingAs;
import ke.sauti.hms.support.FixedClockConfiguration;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Registering, searching and reading patients, and recording allergies, over HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
@ActiveProfiles("test")
class PatientApiTest {

    private static final JsonMapper JSON = JsonMapper.shared();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private VisitService visits;

    private Staff reception;
    private Staff nurse;

    @BeforeEach
    void people() {
        reception = ActingAs.staff(staffRepository, StaffRole.RECEPTIONIST);
        nurse = ActingAs.staff(staffRepository, StaffRole.NURSE);
    }

    private static String randomPhone() {
        return "07" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
    }

    private static String randomNationalId() {
        return String.valueOf(10_000_000 + ThreadLocalRandom.current().nextInt(89_999_999));
    }

    private static Map<String, Object> patient(String name, String phone, String nationalId) {
        Map<String, Object> body = new HashMap<>();
        body.put("fullName", name);
        body.put("dateOfBirth", "1990-05-17");
        body.put("sex", "FEMALE");
        if (phone != null) {
            body.put("phone", phone);
        }
        if (nationalId != null) {
            body.put("nationalId", nationalId);
        }
        return body;
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
    }

    private ResultActions register(Map<String, Object> body) throws Exception {
        return mvc.perform(json(post("/api/patients"), body).with(as(reception)));
    }

    private JsonNode registered(Map<String, Object> body) throws Exception {
        return JSON.readTree(register(body)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private List<JsonNode> search(String query) throws Exception {
        String content = mvc.perform(get("/api/patients").param("q", query).with(as(reception)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JSON.readTree(content).valueStream().toList();
    }

    private static List<String> ids(List<JsonNode> results) {
        return results.stream().map(node -> node.get("id").asString()).toList();
    }

    // ---- register -----------------------------------------------------------------

    @Test
    void registeringGivesAPatientNumberAndNormalisesThePhone() throws Exception {
        String phone = randomPhone();

        JsonNode patient =
                registered(patient("Achieng Odhiambo", phone.substring(0, 4) + " " + phone.substring(4), null));

        assertThat(patient.get("patientNumber").asString()).matches("SH-\\d{6,}");
        assertThat(patient.get("phone").asString()).isEqualTo("254" + phone.substring(1));
        assertThat(patient.get("ageYears").asInt()).isEqualTo(35);
        assertThat(patient.get("preferredLanguage").asString()).isEqualTo("SWAHILI");
        assertThat(patient.get("payerType").asString()).isEqualTo("CASH");
        UUID id = UUID.fromString(patient.get("id").asString());
        assertThat(jdbc.queryForObject(
                        "select count(*) from audit_event where action = 'PATIENT_REGISTERED' and patient_id = ?",
                        Long.class,
                        id))
                .isEqualTo(1);
    }

    @Test
    void phoneAndNationalIdAreUnreadableInTheTable() throws Exception {
        String phone = randomPhone();
        String nationalId = randomNationalId();
        UUID id = UUID.fromString(registered(patient("Wanjiku Kamau", phone, nationalId))
                .get("id")
                .asString());

        Map<String, Object> row = jdbc.queryForMap(
                "select phone, phone_hash, national_id, national_id_hash from patient where id = ?", id);

        String rawPhone = new String((byte[]) row.get("phone"), StandardCharsets.ISO_8859_1);
        String rawId = new String((byte[]) row.get("national_id"), StandardCharsets.ISO_8859_1);
        assertThat(rawPhone).doesNotContain(phone.substring(1));
        assertThat(rawId).doesNotContain(nationalId);
        assertThat((byte[]) row.get("phone_hash")).hasSize(32);
        assertThat((byte[]) row.get("national_id_hash")).hasSize(32);
    }

    @Test
    void theSameNationalIdIsAlwaysRefusedWithTheExistingPatient() throws Exception {
        String nationalId = randomNationalId();
        JsonNode first = registered(patient("Kamau Njoroge", null, nationalId));
        Map<String, Object> again = patient("Kamau N.", null, " " + nationalId + " ");
        again.put("confirmNewPatient", true);

        register(again)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PATIENT"))
                .andExpect(jsonPath("$.confirmable").value(false))
                .andExpect(jsonPath("$.matches[0].id").value(first.get("id").asString()))
                .andExpect(jsonPath("$.matches[0].patientNumber")
                        .value(first.get("patientNumber").asString()))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void aSharedPhoneShowsTheMatchesAndCanBeConfirmedAsADifferentPerson() throws Exception {
        String phone = randomPhone();
        JsonNode mother = registered(patient("Mary Achieng", phone, null));
        Map<String, Object> child = patient("Baby Achieng", "+254" + phone.substring(1), null);

        register(child)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PATIENT"))
                .andExpect(jsonPath("$.confirmable").value(true))
                .andExpect(jsonPath("$.matches[0].id").value(mother.get("id").asString()));

        child.put("confirmNewPatient", true);
        register(child).andExpect(status().isCreated());
    }

    @Test
    void anShaPatientNeedsAnShaNumber() throws Exception {
        Map<String, Object> body = patient("Otieno Okoth", null, null);
        body.put("payerType", "SHA");

        register(body)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("An SHA number is required when SHA pays"));
    }

    @Test
    void badDetailsAreRefusedFieldByField() throws Exception {
        Map<String, Object> body = patient("Future Baby", "0201234567", "12ab");
        body.put("dateOfBirth", "2099-01-01");

        String content = register(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<String> fields = JSON.readTree(content)
                .get("fieldErrors")
                .valueStream()
                .map(node -> node.get("field").asString())
                .toList();
        assertThat(fields).contains("phone", "nationalId", "dateOfBirth");
    }

    // ---- search -------------------------------------------------------------------

    @Test
    void searchFindsByPatientNumberExactly() throws Exception {
        JsonNode patient = registered(patient("Njeri Mwangi", null, null));
        String number = patient.get("patientNumber").asString();

        List<JsonNode> results = search(number.toLowerCase(Locale.ROOT).replace("-", ""));

        assertThat(ids(results)).containsExactly(patient.get("id").asString());
    }

    @Test
    void searchFindsByPhoneWrittenAnyWay() throws Exception {
        String phone = randomPhone();
        JsonNode patient = registered(patient("Chebet Kiprop", phone, null));

        assertThat(ids(search("+254 " + phone.substring(1))))
                .containsExactly(patient.get("id").asString());
        assertThat(ids(search(phone))).containsExactly(patient.get("id").asString());
    }

    @Test
    void searchFindsByNationalId() throws Exception {
        String nationalId = randomNationalId();
        JsonNode patient = registered(patient("Mutua Musyoka", null, nationalId));

        assertThat(ids(search(nationalId))).containsExactly(patient.get("id").asString());
    }

    @Test
    void searchFindsPartOfANameIgnoringCase() throws Exception {
        String unique = "Zawadi" + UUID.randomUUID().toString().substring(0, 6);
        JsonNode patient = registered(patient(unique + " Achieng", null, null));

        List<JsonNode> results = search(unique.substring(0, 9).toUpperCase(Locale.ROOT));

        assertThat(ids(results)).contains(patient.get("id").asString());
        assertThat(results.getFirst().has("phone")).isFalse();
        assertThat(results.getFirst().has("nationalId")).isFalse();
    }

    @Test
    void wildcardsInANameSearchAreTakenLiterally() throws Exception {
        registered(patient("Wildcard Person", null, null));

        assertThat(search("%%")).isEmpty();
        assertThat(search("__")).isEmpty();
    }

    @Test
    void searchReturnsAtMostTwenty() throws Exception {
        String family = "Limit" + UUID.randomUUID().toString().substring(0, 6);
        for (int i = 0; i < 21; i++) {
            registered(patient(family + " Child " + i, null, null));
        }

        assertThat(search(family)).hasSize(20);
    }

    @Test
    void aOneLetterNameSearchIsRefused() throws Exception {
        mvc.perform(get("/api/patients").param("q", "a").with(as(reception)))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void searchesAreAuditedWithoutTheQuery() throws Exception {
        String phone = randomPhone();

        search(phone);

        String detail = jdbc.queryForObject(
                "select detail::text from audit_event where action = 'PATIENT_SEARCH' and actor_id = ?",
                String.class,
                reception.getId());
        assertThat(detail).contains("PHONE_OR_ID").doesNotContain(phone.substring(1));
    }

    // ---- read ---------------------------------------------------------------------

    @Test
    void theDetailsShowTheDecryptedIdentifiers() throws Exception {
        String nationalId = randomNationalId();
        JsonNode patient = registered(patient("Halima Hassan", null, nationalId));

        mvc.perform(get("/api/patients/" + patient.get("id").asString()).with(as(nurse)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nationalId").value(nationalId));
        mvc.perform(get("/api/patients/" + UUID.randomUUID()).with(as(nurse))).andExpect(status().isNotFound());
    }

    // ---- allergies ----------------------------------------------------------------

    /** A patient the nurse is looking after: reception opened a visit and the nurse took it. */
    private String patientId() throws Exception {
        String id = registered(patient("Allergy Patient", null, null)).get("id").asString();
        VisitService.Actor desk = new VisitService.Actor(reception.getId(), StaffRole.RECEPTIONIST);
        UUID visitId = visits.open(UUID.fromString(id), null, desk).getId();
        visits.changeStatus(visitId, VisitStatus.WAITING_TRIAGE, null, desk);
        visits.take(visitId, new VisitService.Actor(nurse.getId(), StaffRole.NURSE));
        return id;
    }

    private ResultActions recordAllergy(String patientId, Map<String, Object> body) throws Exception {
        return mvc.perform(
                json(post("/api/patients/" + patientId + "/allergies"), body).with(as(nurse)));
    }

    private List<JsonNode> allergies(String patientId) throws Exception {
        String content = mvc.perform(
                        get("/api/patients/" + patientId + "/allergies").with(as(nurse)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JSON.readTree(content).valueStream().toList();
    }

    @Test
    void anAllergyIsRecordedWithItsGroupAndReaction() throws Exception {
        String patientId = patientId();

        recordAllergy(
                        patientId,
                        Map.of("substance", " Amoxicillin ", "substanceGroup", "Penicillins", "reaction", "Rash"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("ALLERGY"))
                .andExpect(jsonPath("$.substance").value("Amoxicillin"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(allergies(patientId)).hasSize(1);
    }

    @Test
    void noKnownAllergiesIsItsOwnEntryAndRecordingItTwiceKeepsOne() throws Exception {
        String patientId = patientId();

        String first = JSON.readTree(recordAllergy(patientId, Map.of("noKnownAllergies", true))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.kind").value("NO_KNOWN_ALLERGIES"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asString();
        recordAllergy(patientId, Map.of("noKnownAllergies", true))
                .andExpect(jsonPath("$.id").value(first));

        assertThat(allergies(patientId)).hasSize(1);
    }

    @Test
    void anAllergyRetiresNoKnownAllergies() throws Exception {
        String patientId = patientId();
        recordAllergy(patientId, Map.of("noKnownAllergies", true));

        recordAllergy(patientId, Map.of("substance", "Sulfa")).andExpect(status().isOk());

        List<JsonNode> list = allergies(patientId);
        assertThat(list).hasSize(2);
        JsonNode nka = list.stream()
                .filter(node -> node.get("kind").asString().equals("NO_KNOWN_ALLERGIES"))
                .findFirst()
                .orElseThrow();
        assertThat(nka.get("status").asString()).isEqualTo("ENTERED_IN_ERROR");
        assertThat(nka.get("enteredInErrorReason").asString()).isEqualTo(AllergyService.SUPERSEDED);
    }

    @Test
    void noKnownAllergiesIsRefusedWhileAnAllergyIsActive() throws Exception {
        String patientId = patientId();
        recordAllergy(patientId, Map.of("substance", "Peanuts"));

        recordAllergy(patientId, Map.of("noKnownAllergies", true)).andExpect(status().isUnprocessableContent());
    }

    @Test
    void theSameSubstanceCannotBeActiveTwice() throws Exception {
        String patientId = patientId();
        recordAllergy(patientId, Map.of("substance", "Aspirin"));

        recordAllergy(patientId, Map.of("substance", "aspirin ")).andExpect(status().isConflict());
    }

    @Test
    void anEmptyEntryIsRefused() throws Exception {
        recordAllergy(patientId(), Map.of()).andExpect(status().isUnprocessableContent());
    }

    @Test
    void aWrongEntryIsMarkedEnteredInErrorWithAReasonAndKept() throws Exception {
        String patientId = patientId();
        String allergyId = JSON.readTree(recordAllergy(patientId, Map.of("substance", "Latex"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asString();
        String path = "/api/patients/" + patientId + "/allergies/" + allergyId + "/entered-in-error";

        mvc.perform(json(post(path), Map.of("reason", "Recorded for the wrong patient"))
                        .with(as(nurse)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENTERED_IN_ERROR"))
                .andExpect(jsonPath("$.enteredInErrorReason").value("Recorded for the wrong patient"));
        mvc.perform(json(post(path), Map.of("reason", "Again")).with(as(nurse)))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(json(post(path), Map.of("reason", " ")).with(as(nurse))).andExpect(status().isBadRequest());

        assertThat(allergies(patientId)).hasSize(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from audit_event where action = 'ALLERGY_ENTERED_IN_ERROR' and patient_id = ?",
                        Long.class,
                        UUID.fromString(patientId)))
                .isEqualTo(1);
    }

    @Test
    void allergiesOfAnUnknownPatientAre404() throws Exception {
        mvc.perform(get("/api/patients/" + UUID.randomUUID() + "/allergies").with(as(nurse)))
                .andExpect(status().isNotFound());
    }
}
