package ke.sauti.hms.medication;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import ke.sauti.hms.capture.AudioStore;
import ke.sauti.hms.common.error.BusinessRuleException;
import ke.sauti.hms.common.error.NotFoundException;
import ke.sauti.hms.common.error.ServiceUnavailableException;
import ke.sauti.hms.medication.dto.SpokenInstructionsResponse;
import ke.sauti.hms.medication.dto.SpokenLineResponse;
import ke.sauti.hms.medication.speech.InstructionTemplates;
import ke.sauti.hms.medication.speech.TextToSpeech;
import ke.sauti.hms.patient.PatientService;
import ke.sauti.hms.patient.PreferredLanguage;
import ke.sauti.hms.visit.AccessPolicy;
import ke.sauti.hms.visit.ClinicalData;
import ke.sauti.hms.visit.VisitService;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The patient's instructions, built only from a SIGNED prescription. Audio is cached under a hash
 * of the text and voice. The voice provider is called outside any database transaction.
 */
@Service
public class SpokenInstructions {

    private final PrescriptionRepository prescriptions;
    private final PrescriptionItemRepository items;
    private final MedicineRepository medicines;
    private final PatientService patients;
    private final AccessPolicy accessPolicy;
    private final TextToSpeech speech;
    private final AudioStore store;
    private final TransactionTemplate transaction;

    public SpokenInstructions(
            PrescriptionRepository prescriptions,
            PrescriptionItemRepository items,
            MedicineRepository medicines,
            PatientService patients,
            AccessPolicy accessPolicy,
            TextToSpeech speech,
            AudioStore store,
            PlatformTransactionManager transactionManager) {
        this.prescriptions = prescriptions;
        this.items = items;
        this.medicines = medicines;
        this.patients = patients;
        this.accessPolicy = accessPolicy;
        this.speech = speech;
        this.store = store;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** The text for every line, in the requested language or else the patient's own. */
    public SpokenInstructionsResponse text(
            UUID visitId, @Nullable PreferredLanguage language, VisitService.Actor actor) {
        return Objects.requireNonNull(transaction.execute(status -> read(visitId, language, actor)));
    }

    /** One line's audio, from the cache or else newly made. */
    public byte[] audio(UUID visitId, UUID itemId, @Nullable PreferredLanguage language, VisitService.Actor actor) {
        SpokenInstructionsResponse read = text(visitId, language, actor);
        String spoken = read.lines().stream()
                .filter(line -> line.itemId().equals(itemId))
                .map(SpokenLineResponse::text)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No such medicine on this prescription"));
        return cachedAudio(spoken, read.language());
    }

    /** A failure here is a 503, and the screen then speaks the text with the browser's voice. */
    byte[] cachedAudio(String spoken, PreferredLanguage language) {
        if (!speech.available()) {
            throw new ServiceUnavailableException("No server voice is configured");
        }
        String key = cacheKey(spoken, speech.voice(language));
        try {
            return store.find(key).orElseGet(() -> {
                byte[] audio = speech.synthesise(spoken, language);
                store.put(key, audio);
                return audio;
            });
        } catch (RuntimeException e) {
            throw new ServiceUnavailableException("The voice service is unavailable", e);
        }
    }

    private SpokenInstructionsResponse read(
            UUID visitId, @Nullable PreferredLanguage requested, VisitService.Actor actor) {
        accessPolicy.requireVisitAccess(actor, visitId, ClinicalData.PRESCRIPTION, true);
        Prescription prescription = prescriptions
                .findActiveByVisitId(visitId)
                .orElseThrow(() -> new NotFoundException("No prescription for this visit yet"));
        if (prescription.getStatus() != PrescriptionStatus.SIGNED) {
            throw new BusinessRuleException("Only a signed prescription can be spoken");
        }
        PreferredLanguage language = requested != null
                ? requested
                : patients.get(prescription.getPatientId()).getPreferredLanguage();
        List<PrescriptionItem> lines = items.findByPrescriptionIdOrderByCreatedAtAsc(prescription.getId());
        Map<UUID, Medicine> byId =
                medicines
                        .findAllById(lines.stream()
                                .map(PrescriptionItem::getMedicineId)
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(Medicine::getId, Function.identity()));
        List<SpokenLineResponse> spoken = lines.stream()
                .map(line -> new SpokenLineResponse(
                        line.getId(),
                        InstructionTemplates.render(
                                new InstructionTemplates.Line(
                                        byId.get(line.getMedicineId()).getName(),
                                        line.getDoseValue(),
                                        line.getDoseUnit(),
                                        line.getFrequency(),
                                        line.getDurationDays(),
                                        line.getRoute()),
                                language)))
                .toList();
        return new SpokenInstructionsResponse(language, InstructionTemplates.VERSION, speech.available(), spoken);
    }

    static String cacheKey(String text, String voice) {
        try {
            byte[] hash =
                    MessageDigest.getInstance("SHA-256").digest((voice + "\n" + text).getBytes(StandardCharsets.UTF_8));
            return "instructions/" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is missing", e);
        }
    }
}
