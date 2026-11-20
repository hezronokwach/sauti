package ke.sauti.hms.patient;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import ke.sauti.hms.audit.AuditAction;
import ke.sauti.hms.audit.AuditService;
import ke.sauti.hms.common.crypto.KeyedHasher;
import ke.sauti.hms.common.error.BusinessRuleException;
import ke.sauti.hms.common.error.NotFoundException;
import ke.sauti.hms.patient.dto.PatientSummaryResponse;
import ke.sauti.hms.patient.dto.RegisterPatientRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Registering, finding and reading patients. */
@Service
@Transactional
public class PatientService {

    static final int SEARCH_LIMIT = 20;
    static final int MIN_NAME_QUERY = 2;
    static final int MAX_AGE_YEARS = 130;
    private static final String PATIENT = Patient.class.getSimpleName();
    private static final Pattern DIGITS = Pattern.compile("\\d{6,13}");
    private static final Pattern NATIONAL_ID = Pattern.compile("\\d{6,9}");
    private static final Pattern NUMBER_SEPARATORS = Pattern.compile("[\\s+\\-()]");

    private final PatientRepository repository;
    private final KeyedHasher hasher;
    private final AuditService audit;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public PatientService(
            PatientRepository repository,
            KeyedHasher hasher,
            AuditService audit,
            Clock clock,
            ApplicationEventPublisher events) {
        this.events = events;
        this.repository = repository;
        this.hasher = hasher;
        this.audit = audit;
        this.clock = clock;
    }

    /** How a search was answered, for the audit row (the query itself is never stored). */
    enum SearchRoute {
        NUMBER,
        PHONE_OR_ID,
        NAME
    }

    /**
     * Finds patients. An SH number is matched exactly; digits are matched as a phone number
     * or national ID through their hashes; anything else is a contains-search on the name.
     * At most {@value #SEARCH_LIMIT} results.
     */
    public List<PatientSummaryResponse> search(String query) {
        String text = query == null ? "" : query.trim();
        LocalDate today = LocalDate.now(clock);
        List<Patient> found;
        SearchRoute route;

        Optional<Long> number = PatientNumbers.parse(text);
        String digits = NUMBER_SEPARATORS.matcher(text).replaceAll("");
        if (number.isPresent()) {
            route = SearchRoute.NUMBER;
            found = repository.findByPatientNumber(number.get()).map(List::of).orElse(List.of());
        } else if (DIGITS.matcher(digits).matches()) {
            route = SearchRoute.PHONE_OR_ID;
            found = findByPhoneOrNationalId(digits);
        } else {
            if (text.length() < MIN_NAME_QUERY) {
                throw new BusinessRuleException("Type at least " + MIN_NAME_QUERY + " letters of the name");
            }
            route = SearchRoute.NAME;
            found = repository.findByNameContaining(
                    "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%", PageRequest.of(0, SEARCH_LIMIT));
        }

        audit.record(AuditAction.PATIENT_SEARCH, Map.of("route", route.name(), "results", found.size()));
        return found.stream()
                .limit(SEARCH_LIMIT)
                .map(patient -> PatientSummaryResponse.of(patient, today))
                .toList();
    }

    /**
     * Registers a patient. The same national ID is always refused with the existing
     * patient; the same phone number is refused with the patients using it unless
     * reception confirms this is a different person (a shared family phone).
     */
    public Patient register(RegisterPatientRequest request) {
        LocalDate today = LocalDate.now(clock);
        if (request.dateOfBirth().isBefore(today.minusYears(MAX_AGE_YEARS))) {
            throw new BusinessRuleException("The date of birth is more than " + MAX_AGE_YEARS + " years ago");
        }
        PayerType payer = request.payerType() == null ? PayerType.CASH : request.payerType();
        String shaNumber = blankToNull(request.shaNumber());
        if (payer == PayerType.SHA && shaNumber == null) {
            throw new BusinessRuleException("An SHA number is required when SHA pays");
        }
        String phone = normalisedPhone(request.phone());
        String nationalId = blankToNull(request.nationalId());
        byte[] phoneHash = phone == null ? null : hash(phone);
        byte[] nationalIdHash = nationalId == null ? null : hash(nationalId);

        if (nationalIdHash != null) {
            Optional<Patient> sameId = repository.findByNationalIdHash(nationalIdHash);
            if (sameId.isPresent()) {
                throw new DuplicatePatientException(List.of(PatientSummaryResponse.of(sameId.get(), today)), false);
            }
        }
        if (phoneHash != null && !request.confirmedNewPatient()) {
            List<Patient> samePhone = repository.findByPhoneHashOrderByFullName(phoneHash);
            if (!samePhone.isEmpty()) {
                throw new DuplicatePatientException(
                        samePhone.stream()
                                .map(p -> PatientSummaryResponse.of(p, today))
                                .toList(),
                        true);
            }
        }

        Patient patient = new Patient(
                request.fullName(),
                request.dateOfBirth(),
                request.sex(),
                request.preferredLanguage() == null ? PreferredLanguage.SWAHILI : request.preferredLanguage(),
                payer);
        patient.setPhone(phone, phoneHash);
        patient.setNationalId(nationalId, nationalIdHash);
        patient.setDetails(
                shaNumber,
                blankToNull(request.residence()),
                blankToNull(request.nextOfKinName()),
                normalisedPhone(request.nextOfKinPhone()));
        Patient saved = repository.saveAndFlush(patient);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("payer", payer.name());
        detail.put("sharedPhoneConfirmed", phoneHash != null && request.confirmedNewPatient());
        audit.record(AuditAction.PATIENT_REGISTERED, PATIENT, saved.getId(), saved.getId(), null, detail);
        events.publishEvent(new PatientRegisteredEvent(saved.getId(), nationalId != null));
        return saved;
    }

    /** Reception sets where the patient lives and who to contact, typed or confirmed from a voice draft. */
    public Patient updateContact(UUID id, String residence, String nextOfKinName) {
        Patient patient = repository.findById(id).orElseThrow(() -> new NotFoundException("No such patient"));
        patient.setDetails(
                patient.getShaNumber(),
                blankToNull(residence),
                blankToNull(nextOfKinName),
                patient.getNextOfKinPhone());
        audit.record(AuditAction.PATIENT_CONTACT_CHANGED, PATIENT, id, id, null, Map.of());
        return patient;
    }

    @Transactional(readOnly = true)
    public Patient get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("No such patient"));
    }

    /** The patients with these ids, by id, for lists such as the queues. */
    @Transactional(readOnly = true)
    public Map<UUID, Patient> getAll(Collection<UUID> ids) {
        Map<UUID, Patient> byId = new LinkedHashMap<>();
        repository.findAllById(ids).forEach(patient -> byId.put(patient.getId(), patient));
        return byId;
    }

    private List<Patient> findByPhoneOrNationalId(String digits) {
        List<Patient> found = new ArrayList<>();
        KenyanPhone.normalise(digits)
                .ifPresent(phone -> found.addAll(repository.findByPhoneHashOrderByFullName(hash(phone))));
        if (NATIONAL_ID.matcher(digits).matches()) {
            repository
                    .findByNationalIdHash(hash(digits))
                    .filter(p -> !found.contains(p))
                    .ifPresent(found::add);
        }
        return found;
    }

    private byte[] hash(String value) {
        return hasher.hash(value);
    }

    private static String normalisedPhone(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return KenyanPhone.normalise(raw)
                .orElseThrow(() -> new BusinessRuleException("The phone number is not a Kenyan mobile number"));
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /** Makes user text safe inside a LIKE pattern (escape character backslash). */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
