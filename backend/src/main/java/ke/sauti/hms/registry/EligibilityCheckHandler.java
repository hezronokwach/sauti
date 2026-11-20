package ke.sauti.hms.registry;

import java.time.Clock;
import java.util.UUID;
import ke.sauti.hms.jobs.JobFailure;
import ke.sauti.hms.jobs.JobHandler;
import ke.sauti.hms.jobs.JobQueue;
import ke.sauti.hms.patient.Patient;
import ke.sauti.hms.patient.PatientRegisteredEvent;
import ke.sauti.hms.patient.PatientService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * SHA eligibility in the background. A patient registered with a national ID gets a PENDING
 * answer and a queued check, in the registration transaction. The check stores ELIGIBLE,
 * INELIGIBLE or NOT_FOUND; when the service does not answer it stores UNAVAILABLE and lets
 * the job engine retry. Safe to run twice: it only overwrites the answer.
 */
@Component
public class EligibilityCheckHandler implements JobHandler {

    public static final String TYPE = "ELIGIBILITY_CHECK";

    private final Eligibility eligibility;
    private final PatientEligibilityRepository answers;
    private final PatientService patients;
    private final JobQueue jobs;
    private final Clock clock;

    public EligibilityCheckHandler(
            Eligibility eligibility,
            PatientEligibilityRepository answers,
            PatientService patients,
            JobQueue jobs,
            Clock clock) {
        this.eligibility = eligibility;
        this.answers = answers;
        this.patients = patients;
        this.jobs = jobs;
        this.clock = clock;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onRegistered(PatientRegisteredEvent event) {
        if (event.hasNationalId()) {
            answers.save(new PatientEligibility(event.patientId()));
            jobs.enqueue(TYPE, event.patientId());
        }
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void handle(UUID patientId) {
        Patient patient = patients.get(patientId);
        PatientEligibility answer = answers.findByPatientId(patientId)
                .orElseThrow(() -> new JobFailure.Permanent("No eligibility row for this patient"));
        if (!StringUtils.hasText(patient.getNationalId())) {
            throw new JobFailure.Permanent("The patient has no national ID");
        }
        try {
            answer.answer(eligibility.check(patient.getNationalId()), clock.instant());
            answers.save(answer);
        } catch (RegistryUnavailableException e) {
            answer.answer(EligibilityStatus.UNAVAILABLE, clock.instant());
            answers.save(answer);
            throw new IllegalStateException("The eligibility service did not answer", e);
        }
    }
}
