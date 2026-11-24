package ke.sauti.hms.billing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ke.sauti.hms.audit.AccessKind;
import ke.sauti.hms.audit.AccessLogService;
import ke.sauti.hms.audit.AuditAction;
import ke.sauti.hms.audit.AuditService;
import ke.sauti.hms.billing.dto.InvoiceLineResponse;
import ke.sauti.hms.billing.dto.InvoiceResponse;
import ke.sauti.hms.capture.CaptureService;
import ke.sauti.hms.common.error.BusinessRuleException;
import ke.sauti.hms.common.error.ConflictException;
import ke.sauti.hms.common.error.NotFoundException;
import ke.sauti.hms.medication.BillableMedicine;
import ke.sauti.hms.medication.PrescriptionService;
import ke.sauti.hms.note.NoteService;
import ke.sauti.hms.visit.AccessPolicy;
import ke.sauti.hms.visit.ClinicalData;
import ke.sauti.hms.visit.Visit;
import ke.sauti.hms.visit.VisitService;
import ke.sauti.hms.visit.VisitStatus;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The bill for a visit. Finishing a consultation builds it; cash or insurance settles it. Every
 * step locks the visit first, so a double click or two cashiers can never pay a bill twice. The
 * cashier sees medicine names, quantities and prices, never a dose, note or reason for visit.
 */
@Service
@Transactional
@EnableConfigurationProperties(BillingProperties.class)
public class BillingService {

    private static final String INVOICE = Invoice.class.getSimpleName();

    private final InvoiceRepository invoices;
    private final InvoiceLineRepository lines;
    private final PaymentRepository payments;
    private final VisitService visits;
    private final AccessPolicy accessPolicy;
    private final NoteService notes;
    private final PrescriptionService prescriptions;
    private final CaptureService captures;
    private final AccessLogService accessLog;
    private final AuditService audit;
    private final BillingProperties properties;

    public BillingService(
            InvoiceRepository invoices,
            InvoiceLineRepository lines,
            PaymentRepository payments,
            VisitService visits,
            AccessPolicy accessPolicy,
            NoteService notes,
            PrescriptionService prescriptions,
            CaptureService captures,
            AccessLogService accessLog,
            AuditService audit,
            BillingProperties properties) {
        this.invoices = invoices;
        this.lines = lines;
        this.payments = payments;
        this.visits = visits;
        this.accessPolicy = accessPolicy;
        this.notes = notes;
        this.prescriptions = prescriptions;
        this.captures = captures;
        this.accessLog = accessLog;
        this.audit = audit;
        this.properties = properties;
    }

    /** Ends the consultation: checks it is complete, builds the bill and sends the visit to payment. */
    public InvoiceResponse finishConsultation(UUID visitId, VisitService.Actor actor) {
        Visit visit = visits.lockForChange(visitId);
        accessPolicy.requireVisitAccess(actor, visitId, ClinicalData.NOTE, false);
        if (visit.getStatus() != VisitStatus.WITH_DOCTOR) {
            throw new ConflictException("This consultation is not in progress");
        }
        if (!notes.isSigned(visitId)) {
            throw new BusinessRuleException("Sign the note before finishing the consultation");
        }
        if (captures.isRecording(visitId)) {
            throw new BusinessRuleException("Stop the recording before finishing the consultation");
        }
        List<BillableMedicine> medicines = prescriptions.billableLines(visitId);
        visits.changeStatus(visitId, VisitStatus.WAITING_PAYMENT, null, actor);

        BigDecimal total = properties.consultationFeeKes();
        for (BillableMedicine medicine : medicines) {
            total = total.add(medicine.unitPriceKes().multiply(BigDecimal.valueOf(medicine.quantity())));
        }
        Invoice invoice = invoices.saveAndFlush(new Invoice(visitId, visit.getPatientId(), total, actor.staffId()));
        List<InvoiceLine> built = new ArrayList<>();
        built.add(new InvoiceLine(
                invoice.getId(), 0, InvoiceLineKind.CONSULTATION, "Consultation", 1, properties.consultationFeeKes()));
        for (BillableMedicine medicine : medicines) {
            built.add(new InvoiceLine(
                    invoice.getId(),
                    built.size(),
                    InvoiceLineKind.MEDICINE,
                    medicine.description(),
                    medicine.quantity(),
                    medicine.unitPriceKes()));
        }
        lines.saveAllAndFlush(built);
        audit.record(
                AuditAction.INVOICE_CREATED,
                INVOICE,
                invoice.getId(),
                invoice.getPatientId(),
                visitId,
                Map.of("lines", built.size()));
        return respond(invoice);
    }

    /** The bill for the cashier. It names medicines, so every read is access-logged. */
    public InvoiceResponse get(UUID visitId) {
        Invoice invoice = require(visitId);
        accessLog.record(invoice.getPatientId(), visitId, AccessKind.INVOICE_OPENED);
        return respond(invoice);
    }

    /** Cash for the full total: the bill is paid and the visit ends. */
    public InvoiceResponse payCash(UUID visitId, String idempotencyKey, VisitService.Actor actor) {
        return settle(visitId, idempotencyKey, PaymentMethod.CASH, PaymentStatus.PAID, InvoiceStatus.PAID, actor);
    }

    /** Billed to insurance: the claim stays pending and the visit ends. */
    public InvoiceResponse billInsurance(UUID visitId, String idempotencyKey, VisitService.Actor actor) {
        return settle(
                visitId,
                idempotencyKey,
                PaymentMethod.INSURANCE,
                PaymentStatus.PENDING,
                InvoiceStatus.INSURANCE_PENDING,
                actor);
    }

    private InvoiceResponse settle(
            UUID visitId,
            String idempotencyKey,
            PaymentMethod method,
            PaymentStatus paymentStatus,
            InvoiceStatus invoiceStatus,
            VisitService.Actor actor) {
        Visit visit = visits.lockForChange(visitId);
        Invoice invoice = require(visitId);
        Payment earlier = payments.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (earlier != null) {
            if (!earlier.getInvoiceId().equals(invoice.getId()) || earlier.getMethod() != method) {
                throw new ConflictException("This payment request was already used for something else");
            }
            return respond(invoice);
        }
        if (invoice.getStatus() != InvoiceStatus.UNPAID || visit.getStatus() != VisitStatus.WAITING_PAYMENT) {
            throw new ConflictException("This invoice has already been settled");
        }
        // An M-Pesa prompt may already have taken the money, so it must be settled first.
        if (!payments.findByInvoiceIdAndStatusNot(invoice.getId(), PaymentStatus.FAILED)
                .isEmpty()) {
            throw new ConflictException("An M-Pesa payment for this invoice is still open");
        }
        Payment payment = payments.saveAndFlush(new Payment(
                invoice.getId(), method, invoice.getTotalKes(), paymentStatus, idempotencyKey, actor.staffId()));
        invoice.settle(invoiceStatus);
        visits.changeStatus(visitId, VisitStatus.DONE, null, actor);
        audit.record(
                AuditAction.PAYMENT_RECORDED,
                Payment.class.getSimpleName(),
                payment.getId(),
                invoice.getPatientId(),
                visitId,
                Map.of("method", method.name(), "status", paymentStatus.name()));
        return respond(invoice);
    }

    private Invoice require(UUID visitId) {
        return invoices.findByVisitId(visitId).orElseThrow(() -> new NotFoundException("No invoice for this visit"));
    }

    private InvoiceResponse respond(Invoice invoice) {
        PaymentMethod method = payments.findByInvoiceIdAndStatusNot(invoice.getId(), PaymentStatus.FAILED).stream()
                .map(Payment::getMethod)
                .findFirst()
                .orElse(null);
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getVisitId(),
                invoice.getStatus(),
                invoice.getTotalKes(),
                method,
                lines.findByInvoiceIdOrderByPositionAsc(invoice.getId()).stream()
                        .map(InvoiceLineResponse::of)
                        .toList());
    }
}
