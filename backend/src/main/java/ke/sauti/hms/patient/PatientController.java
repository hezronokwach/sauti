package ke.sauti.hms.patient;

import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ke.sauti.hms.patient.dto.PatientDetailsResponse;
import ke.sauti.hms.patient.dto.PatientSummaryResponse;
import ke.sauti.hms.patient.dto.RegisterPatientRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Patient registration and lookup. Reception registers; the staff who need to find a
 * patient (reception, nurses, doctors, cashiers) search and read. Admins do not see
 * patients. The same rules are URL rules in the security configuration, checked before
 * the request body is read.
 */
@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService service;
    private final Clock clock;

    public PatientController(PatientService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'NURSE', 'DOCTOR', 'CASHIER')")
    public List<PatientSummaryResponse> search(@RequestParam("q") String query) {
        return service.search(query);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public PatientDetailsResponse register(@Valid @RequestBody RegisterPatientRequest request) {
        return PatientDetailsResponse.of(service.register(request), LocalDate.now(clock));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'NURSE', 'DOCTOR', 'CASHIER')")
    public PatientDetailsResponse get(@PathVariable UUID id) {
        return PatientDetailsResponse.of(service.get(id), LocalDate.now(clock));
    }
}
