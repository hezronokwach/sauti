package ke.sauti.hms.visit;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ke.sauti.hms.audit.AuditActor;
import ke.sauti.hms.patient.Patient;
import ke.sauti.hms.patient.PatientNumbers;
import ke.sauti.hms.patient.PatientService;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.visit.dto.ChangeVisitStatusRequest;
import ke.sauti.hms.visit.dto.OpenVisitRequest;
import ke.sauti.hms.visit.dto.QueueEntryResponse;
import ke.sauti.hms.visit.dto.ReasonForVisitResponse;
import ke.sauti.hms.visit.dto.UpdateReasonRequest;
import ke.sauti.hms.visit.dto.VisitResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Visits and queues. Reception opens visits; nurses and doctors take patients from their
 * queue; each role moves the visit on as {@link VisitWorkflow} allows. Admins have no
 * part in visits. The same rules are URL rules in the security configuration.
 */
@RestController
@RequestMapping("/api/visits")
@PreAuthorize("hasAnyRole('RECEPTIONIST', 'NURSE', 'DOCTOR', 'CASHIER')")
public class VisitController {

    private final VisitService service;
    private final PatientService patients;

    public VisitController(VisitService service, PatientService patients) {
        this.service = service;
        this.patients = patients;
    }

    private static VisitService.Actor actor(AuditActor staff) {
        return new VisitService.Actor(staff.actorId(), StaffRole.valueOf(staff.actorRole()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public VisitResponse open(@Valid @RequestBody OpenVisitRequest request, @AuthenticationPrincipal AuditActor staff) {
        return VisitResponse.of(
                service.open(request.patientId(), request.reasonForVisit(), request.ticketId(), actor(staff)));
    }

    /** Today's queue for one status, in queue order. */
    @GetMapping
    public List<QueueEntryResponse> queue(@RequestParam("status") VisitStatus status) {
        List<Visit> visits = service.queue(status);
        Map<UUID, Patient> byId =
                patients.getAll(visits.stream().map(Visit::getPatientId).toList());
        return visits.stream()
                .map(visit -> {
                    Patient patient = byId.get(visit.getPatientId());
                    return new QueueEntryResponse(
                            visit.getId(),
                            visit.getQueueNumber(),
                            visit.getStatus(),
                            visit.getPatientId(),
                            patient.getFullName(),
                            PatientNumbers.format(patient.getPatientNumber()),
                            visit.getStatusChangedAt());
                })
                .toList();
    }

    @GetMapping("/{id}")
    public VisitResponse get(@PathVariable UUID id) {
        return VisitResponse.of(service.get(id));
    }

    /** Clinical: only for someone looking after the visit (see AccessPolicy). */
    @GetMapping("/{id}/reason")
    public ReasonForVisitResponse reason(@PathVariable UUID id, @AuthenticationPrincipal AuditActor staff) {
        String reason = service.reasonForVisit(id, actor(staff));
        return new ReasonForVisitResponse(reason, service.get(id).isReasonConfirmed());
    }

    @PutMapping("/{id}/reason")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'NURSE')")
    public ReasonForVisitResponse changeReason(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateReasonRequest request,
            @AuthenticationPrincipal AuditActor staff) {
        Visit visit = service.changeReason(id, request.reasonForVisit(), actor(staff));
        return new ReasonForVisitResponse(request.reasonForVisit().strip(), visit.isReasonConfirmed());
    }

    @PostMapping("/{id}/reason/confirm")
    @PreAuthorize("hasRole('NURSE')")
    public ReasonForVisitResponse confirmReason(@PathVariable UUID id, @AuthenticationPrincipal AuditActor staff) {
        service.confirmReason(id, actor(staff));
        return new ReasonForVisitResponse(service.reasonForVisit(id, actor(staff)), true);
    }

    @PostMapping("/{id}/take")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR')")
    public VisitResponse take(@PathVariable UUID id, @AuthenticationPrincipal AuditActor staff) {
        return VisitResponse.of(service.take(id, actor(staff)));
    }

    @PostMapping("/{id}/status")
    public VisitResponse changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeVisitStatusRequest request,
            @AuthenticationPrincipal AuditActor staff) {
        return VisitResponse.of(service.changeStatus(id, request.to(), request.reason(), actor(staff)));
    }
}
