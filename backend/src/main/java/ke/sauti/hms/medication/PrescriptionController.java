package ke.sauti.hms.medication;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import ke.sauti.hms.audit.AuditActor;
import ke.sauti.hms.medication.dto.MedicineResponse;
import ke.sauti.hms.medication.dto.OverrideAlertRequest;
import ke.sauti.hms.medication.dto.PrescriptionItemRequest;
import ke.sauti.hms.medication.dto.PrescriptionResponse;
import ke.sauti.hms.medication.dto.SignPrescriptionRequest;
import ke.sauti.hms.medication.dto.SpokenInstructionsResponse;
import ke.sauti.hms.patient.PreferredLanguage;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.visit.VisitService;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The medicine search and a visit's prescription. Doctors write; the care team reads. */
@RestController
public class PrescriptionController {

    private static final String PRESCRIPTION = "/api/visits/{visitId}/prescription";
    private static final String ITEMS = PRESCRIPTION + "/items";
    private static final String ALERT = PRESCRIPTION + "/alerts/{alertId}";

    private final PrescriptionService service;
    private final SpokenInstructions spoken;

    public PrescriptionController(PrescriptionService service, SpokenInstructions spoken) {
        this.service = service;
        this.spoken = spoken;
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

    @PostMapping(ALERT + "/override")
    @PreAuthorize("hasRole('DOCTOR')")
    public PrescriptionResponse overrideAlert(
            @PathVariable UUID visitId,
            @PathVariable UUID alertId,
            @Valid @RequestBody OverrideAlertRequest request,
            @AuthenticationPrincipal AuditActor staff) {
        return service.overrideAlert(visitId, alertId, request.reason(), request.code(), actor(staff));
    }

    @PostMapping(ALERT + "/acknowledge")
    @PreAuthorize("hasRole('DOCTOR')")
    public PrescriptionResponse acknowledgeAlert(
            @PathVariable UUID visitId, @PathVariable UUID alertId, @AuthenticationPrincipal AuditActor staff) {
        return service.acknowledgeAlert(visitId, alertId, actor(staff));
    }

    @PostMapping(PRESCRIPTION + "/sign")
    @PreAuthorize("hasRole('DOCTOR')")
    public PrescriptionResponse sign(
            @PathVariable UUID visitId,
            @Valid @RequestBody SignPrescriptionRequest request,
            @AuthenticationPrincipal AuditActor staff) {
        return service.sign(visitId, request.revision(), request.code(), actor(staff));
    }

    /** The signed prescription as words for the patient. */
    @GetMapping(PRESCRIPTION + "/instructions")
    @PreAuthorize("hasRole('DOCTOR')")
    public SpokenInstructionsResponse instructions(
            @PathVariable UUID visitId,
            @RequestParam(required = false) @Nullable PreferredLanguage language,
            @AuthenticationPrincipal AuditActor staff) {
        return spoken.text(visitId, language, actor(staff));
    }

    /** One line's audio. It holds a patient's medicine, so the browser must not cache it. */
    @GetMapping(PRESCRIPTION + "/instructions/{itemId}/audio")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<byte[]> instructionAudio(
            @PathVariable UUID visitId,
            @PathVariable UUID itemId,
            @RequestParam(required = false) @Nullable PreferredLanguage language,
            @AuthenticationPrincipal AuditActor staff) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .cacheControl(CacheControl.noStore())
                .body(spoken.audio(visitId, itemId, language, actor(staff)));
    }

    @PostMapping(PRESCRIPTION + "/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('DOCTOR')")
    public void cancel(
            @PathVariable UUID visitId,
            @Valid @RequestBody SignPrescriptionRequest request,
            @AuthenticationPrincipal AuditActor staff) {
        service.cancel(visitId, request.revision(), request.code(), actor(staff));
    }
}
