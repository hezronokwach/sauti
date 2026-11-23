package ke.sauti.hms.billing;

import jakarta.validation.Valid;
import java.util.UUID;
import ke.sauti.hms.audit.AuditActor;
import ke.sauti.hms.billing.dto.InvoiceResponse;
import ke.sauti.hms.billing.dto.RecordPaymentRequest;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.visit.VisitService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The doctor finishes a consultation; the cashier reads and settles its bill. */
@RestController
public class BillingController {

    private static final String INVOICE = "/api/visits/{visitId}/invoice";

    private final BillingService service;

    public BillingController(BillingService service) {
        this.service = service;
    }

    private static VisitService.Actor actor(AuditActor staff) {
        return new VisitService.Actor(staff.actorId(), StaffRole.valueOf(staff.actorRole()));
    }

    @PostMapping("/api/visits/{visitId}/finish")
    @PreAuthorize("hasRole('DOCTOR')")
    public InvoiceResponse finish(@PathVariable UUID visitId, @AuthenticationPrincipal AuditActor staff) {
        return service.finishConsultation(visitId, actor(staff));
    }

    @GetMapping(INVOICE)
    @PreAuthorize("hasRole('CASHIER')")
    public InvoiceResponse invoice(@PathVariable UUID visitId) {
        return service.get(visitId);
    }

    @PostMapping(INVOICE + "/cash")
    @PreAuthorize("hasRole('CASHIER')")
    public InvoiceResponse payCash(
            @PathVariable UUID visitId,
            @Valid @RequestBody RecordPaymentRequest request,
            @AuthenticationPrincipal AuditActor staff) {
        return service.payCash(visitId, request.idempotencyKey(), actor(staff));
    }

    @PostMapping(INVOICE + "/insurance")
    @PreAuthorize("hasRole('CASHIER')")
    public InvoiceResponse billInsurance(
            @PathVariable UUID visitId,
            @Valid @RequestBody RecordPaymentRequest request,
            @AuthenticationPrincipal AuditActor staff) {
        return service.billInsurance(visitId, request.idempotencyKey(), actor(staff));
    }
}
