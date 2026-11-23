package ke.sauti.hms.medication;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import ke.sauti.hms.audit.AuditActor;
import ke.sauti.hms.medication.dto.MedicineResponse;
import ke.sauti.hms.medication.dto.PrescriptionItemRequest;
import ke.sauti.hms.medication.dto.PrescriptionResponse;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.visit.VisitService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The medicine search and a visit's prescription. Doctors write; the care team reads. */
@RestController
public class PrescriptionController {

    private static final String ITEMS = "/api/visits/{visitId}/prescription/items";

    private final PrescriptionService service;

    public PrescriptionController(PrescriptionService service) {
        this.service = service;
    }

    private static VisitService.Actor actor(AuditActor staff) {
        return new VisitService.Actor(staff.actorId(), StaffRole.valueOf(staff.actorRole()));
    }

    @GetMapping("/api/medicines")
    @PreAuthorize("hasRole('DOCTOR')")
    public List<MedicineResponse> search(@RequestParam("q") @Size(max = 60) String text) {
        return service.searchMedicines(text);
    }

    @GetMapping("/api/visits/{visitId}/prescription")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'CASHIER')")
    public PrescriptionResponse get(@PathVariable UUID visitId, @AuthenticationPrincipal AuditActor staff) {
        return service.get(visitId, actor(staff));
    }

    @PostMapping(ITEMS)
    @PreAuthorize("hasRole('DOCTOR')")
    public PrescriptionResponse addItem(
            @PathVariable UUID visitId,
            @Valid @RequestBody PrescriptionItemRequest request,
            @AuthenticationPrincipal AuditActor staff) {
        return service.addItem(visitId, request, actor(staff));
    }

    @PutMapping(ITEMS + "/{itemId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public PrescriptionResponse updateItem(
            @PathVariable UUID visitId,
            @PathVariable UUID itemId,
            @Valid @RequestBody PrescriptionItemRequest request,
            @AuthenticationPrincipal AuditActor staff) {
        return service.updateItem(visitId, itemId, request, actor(staff));
    }

    @DeleteMapping(ITEMS + "/{itemId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public PrescriptionResponse removeItem(
            @PathVariable UUID visitId,
            @PathVariable UUID itemId,
            @RequestParam @PositiveOrZero int revision,
            @AuthenticationPrincipal AuditActor staff) {
        return service.removeItem(visitId, itemId, revision, actor(staff));
    }
}
