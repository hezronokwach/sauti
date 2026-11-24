package ke.sauti.hms.visit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.common.error.ConflictException;
import ke.sauti.hms.patient.PatientService;
import ke.sauti.hms.patient.Sex;
import ke.sauti.hms.patient.dto.RegisterPatientRequest;
import ke.sauti.hms.staff.StaffRepository;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.support.ActingAs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Several people acting on the same thing at the same instant, on real PostgreSQL. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class VisitConcurrencyTest {

    @Autowired
    private VisitService visits;

    @Autowired
    private PatientService patients;

    @Autowired
    private StaffRepository staffRepository;

    private UUID newPatient() {
        return patients.register(new RegisterPatientRequest(
                        "Busy Patient " + UUID.randomUUID().toString().substring(0, 6),
                        LocalDate.of(1970, 1, 1),
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
    }

    private VisitService.Actor actor(StaffRole role) {
        return new VisitService.Actor(ActingAs.staff(staffRepository, role).getId(), role);
    }

    /** Runs the tasks together, released at the same moment; returns each outcome. */
    private static <T> List<Object> race(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<T> future : futures) {
                try {
                    outcomes.add(future.get());
                } catch (ExecutionException e) {
                    outcomes.add(e.getCause());
                }
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void twoDoctorsTakingThePatientAtOnceExactlyOneSucceeds() throws Exception {
        VisitService.Actor reception = actor(StaffRole.RECEPTIONIST);
        VisitService.Actor nurse = actor(StaffRole.NURSE);
        UUID visitId = visits.open(newPatient(), null, reception).getId();
        visits.changeStatus(visitId, VisitStatus.WAITING_TRIAGE, null, reception);
        visits.take(visitId, nurse);
        visits.changeStatus(visitId, VisitStatus.WAITING_DOCTOR, null, nurse);
        VisitService.Actor first = actor(StaffRole.DOCTOR);
        VisitService.Actor second = actor(StaffRole.DOCTOR);

        List<Object> outcomes = race(List.of(() -> visits.take(visitId, first), () -> visits.take(visitId, second)));

        assertThat(outcomes).filteredOn(Visit.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(ConflictException.class::isInstance).hasSize(1);
        Visit taken = visits.get(visitId);
        assertThat(taken.getStatus()).isEqualTo(VisitStatus.WITH_DOCTOR);
        assertThat(visits.assignments(visitId))
                .filteredOn(CareAssignment::isOpen)
                .hasSize(1);
    }

    @Test
    void visitsOpenedAtOnceGetDifferentQueueNumbers() throws Exception {
        VisitService.Actor reception = actor(StaffRole.RECEPTIONIST);
        List<UUID> patientIds = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            patientIds.add(newPatient());
        }
        List<Callable<Visit>> opens = new ArrayList<>();
        for (UUID patientId : patientIds) {
            opens.add(() -> visits.open(patientId, null, reception));
        }

        List<Object> outcomes = race(opens);

        assertThat(outcomes).allMatch(Visit.class::isInstance);
        HashSet<Integer> numbers = new HashSet<>();
        outcomes.forEach(visit -> numbers.add(((Visit) visit).getQueueNumber()));
        assertThat(numbers).hasSize(8);
    }
}
