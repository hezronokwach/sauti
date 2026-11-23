package ke.sauti.hms.billing;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import ke.sauti.hms.audit.AuditAction;
import ke.sauti.hms.audit.AuditService;
import ke.sauti.hms.billing.dto.PaymentStatusResponse;
import ke.sauti.hms.billing.mpesa.MpesaGateway;
import ke.sauti.hms.billing.mpesa.MpesaRejectedException;
import ke.sauti.hms.common.error.BusinessRuleException;
import ke.sauti.hms.common.error.ConflictException;
import ke.sauti.hms.common.error.NotFoundException;
import ke.sauti.hms.jobs.JobQueue;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.visit.Visit;
import ke.sauti.hms.visit.VisitService;
import ke.sauti.hms.visit.VisitStatus;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * M-Pesa payments. A callback alone never marks anything paid:
 *
 * <pre>
 * PENDING -> PAID         the status query says success AND the amount equals the bill
 *         -> FAILED       the status query says it failed, or the prompt was refused
 *         -> UNCONFIRMED  no clear answer after 10 minutes, a wrong amount, or a lost connection
 * </pre>
 *
 * Provider calls happen outside database transactions. An UNCONFIRMED payment still blocks a new
 * prompt, because the customer may already have paid, until the cashier checks the statement.
 */
@Service
public class MpesaService {

    static final String VERIFY_JOB = "MPESA_VERIFY";
    static final Duration RETRY_EVERY = Duration.ofSeconds(15);
    static final Duration GIVE_UP_AFTER = Duration.ofMinutes(10);
    static final Duration SWEEP_AFTER = Duration.ofSeconds(90);

    private static final Logger log = LoggerFactory.getLogger(MpesaService.class);
    private static final String PAYMENT = Payment.class.getSimpleName();

    private final PaymentRepository payments;
    private final InvoiceRepository invoices;
    private final VisitService visits;
    private final MpesaGateway gateway;
    private final JobQueue jobs;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public MpesaService(
            PaymentRepository payments,
            InvoiceRepository invoices,
            VisitService visits,
            MpesaGateway gateway,
            JobQueue jobs,
            AuditService audit,
            JdbcTemplate jdbc,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.payments = payments;
        this.invoices = invoices;
        this.visits = visits;
        this.gateway = gateway;
        this.jobs = jobs;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** What the first step decided: a prompt to send, or an earlier request's payment. */
    private record Started(UUID paymentId, BigDecimal amount, String reference, boolean send) {}

    /** Saves PENDING, then sends the prompt. A repeated key sends nothing. */
    public PaymentStatusResponse pay(UUID visitId, String phone, String idempotencyKey, VisitService.Actor actor) {
        Started started = Objects.requireNonNull(transaction.execute(tx -> start(visitId, idempotencyKey, actor)));
        if (started.send()) {
            sendPrompt(started, phone);
        }
        return status(visitId);
    }

    private Started start(UUID visitId, String idempotencyKey, VisitService.Actor actor) {
        Visit visit = visits.lockForChange(visitId);
        Invoice invoice = requireInvoice(visitId);
        Payment earlier = payments.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (earlier != null) {
            if (!earlier.getInvoiceId().equals(invoice.getId()) || earlier.getMethod() != PaymentMethod.MPESA) {
                throw new ConflictException("This payment request was already used for something else");
            }
            return new Started(earlier.getId(), earlier.getAmountKes(), "", false);
        }
        if (invoice.getStatus() != InvoiceStatus.UNPAID || visit.getStatus() != VisitStatus.WAITING_PAYMENT) {
            throw new ConflictException("This invoice has already been settled");
        }
        if (!payments.findByInvoiceIdAndStatusNot(invoice.getId(), PaymentStatus.FAILED)
                .isEmpty()) {
            throw new ConflictException("A payment prompt has already been sent for this invoice");
        }
        BigDecimal amount = invoice.getTotalKes();
        if (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0) {
            throw new BusinessRuleException("M-Pesa needs a whole number of shillings above zero; take cash instead");
        }
        Payment payment = new Payment(
                invoice.getId(), PaymentMethod.MPESA, amount, PaymentStatus.PENDING, idempotencyKey, actor.staffId());
        payment.prompted(clock.instant());
        Payment saved = payments.saveAndFlush(payment);
        return new Started(saved.getId(), amount, saved.getId().toString().replace("-", ""), true);
    }

    private void sendPrompt(Started started, String phone) {
        try {
            String checkoutId = gateway.stkPush(phone, started.amount(), started.reference());
            transaction.executeWithoutResult(
                    tx -> requirePayment(started.paymentId()).attachCheckout(checkoutId));
        } catch (MpesaRejectedException e) {
            log.warn("M-Pesa refused the prompt for payment {}", started.paymentId());
            finishWithoutPaying(started.paymentId(), PaymentStatus.FAILED);
        } catch (RuntimeException e) {
            // The prompt may have reached the phone, so this must not look like a clean failure.
            log.warn("M-Pesa prompt for payment {} has no clear answer", started.paymentId());
            finishWithoutPaying(started.paymentId(), PaymentStatus.UNCONFIRMED);
        }
    }

    private void finishWithoutPaying(UUID paymentId, PaymentStatus outcome) {
        transaction.executeWithoutResult(tx -> {
            Payment payment = payments.lockById(paymentId).orElseThrow();
            if (payment.getStatus() == PaymentStatus.PENDING) {
                close(payment, outcome);
            }
        });
    }

    /**
     * A callback from M-Pesa. Only its claims are stored and one verification queued, so repeats
     * change nothing. An unknown checkout id is ignored.
     */
    public void onCallback(String checkoutId, @Nullable BigDecimal amount, @Nullable String receipt) {
        transaction.executeWithoutResult(tx -> payments.lockByCheckoutRequestId(checkoutId)
                .filter(payment -> payment.getStatus() == PaymentStatus.PENDING)
                .ifPresent(payment -> {
                    Instant now = clock.instant();
                    payment.recordCallback(amount, receipt, now);
                    if (payment.getVerificationQueuedAt() == null) {
                        payment.verificationQueued(now);
                        jobs.enqueue(VERIFY_JOB, payment.getId());
                    }
                }));
    }

    /** Queues verification for prompts with no callback after 90 seconds; returns how many. */
    public int sweep() {
        Instant now = clock.instant();
        return Objects.requireNonNull(transaction.execute(tx -> {
            List<UUID> due = jdbc.queryForList(
                    "update payment set verification_queued_at = ? where id in (select id from payment"
                            + " where status = 'PENDING' and method = 'MPESA' and verification_queued_at is null"
                            + " and checkout_request_id is not null and prompted_at <= ?"
                            + " for update skip locked) returning id",
                    UUID.class,
                    Timestamp.from(now),
                    Timestamp.from(now.minus(SWEEP_AFTER)));
            due.forEach(id -> jobs.enqueue(VERIFY_JOB, id));
            return due.size();
        }));
    }

    /** Asks M-Pesa how the prompt ended, outside a transaction, then records it. Safe to repeat. */
    public void verify(UUID paymentId) {
        String checkoutId = transaction.execute(tx -> payments.findById(paymentId)
                .filter(payment -> payment.getStatus() == PaymentStatus.PENDING)
                .map(Payment::getCheckoutRequestId)
                .orElse(null));
        if (checkoutId == null) {
            return;
        }
        MpesaGateway.QueryResult result;
        try {
            result = gateway.query(checkoutId);
        } catch (RuntimeException e) {
            log.warn("M-Pesa status query failed for payment {}", paymentId);
            result = new MpesaGateway.QueryResult(MpesaGateway.Outcome.PROCESSING, null, null);
        }
        MpesaGateway.QueryResult answer = result;
        transaction.executeWithoutResult(tx -> record(paymentId, answer));
    }

    private void record(UUID paymentId, MpesaGateway.QueryResult result) {
        UUID visitId = payments.findVisitId(paymentId).orElseThrow();
        visits.lockForChange(visitId);
        Payment payment = payments.lockById(paymentId).orElseThrow();
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }
        switch (result.outcome()) {
            case FAILED -> close(payment, PaymentStatus.FAILED);
            case PROCESSING -> retryOrGiveUp(payment);
            case SUCCESS -> {
                BigDecimal amount = result.amountKes() != null ? result.amountKes() : payment.getCallbackAmountKes();
                String receipt = result.receipt() != null ? result.receipt() : payment.getCallbackReceipt();
                if (amount == null || receipt == null) {
                    retryOrGiveUp(payment);
                } else if (amount.compareTo(payment.getAmountKes()) != 0) {
                    log.warn("M-Pesa amount does not match the bill for payment {}", paymentId);
                    close(payment, PaymentStatus.UNCONFIRMED);
                } else {
                    markPaid(payment, receipt, visitId);
                }
            }
        }
    }

    private void retryOrGiveUp(Payment payment) {
        Instant now = clock.instant();
        if (!now.isBefore(Objects.requireNonNull(payment.getPromptedAt()).plus(GIVE_UP_AFTER))) {
            close(payment, PaymentStatus.UNCONFIRMED);
        } else {
            jobs.enqueueAt(VERIFY_JOB, payment.getId(), now.plus(RETRY_EVERY));
        }
    }

    private void markPaid(Payment payment, String receipt, UUID visitId) {
        payment.markPaid(receipt);
        Invoice invoice = invoices.findById(payment.getInvoiceId()).orElseThrow();
        invoice.settle(InvoiceStatus.PAID);
        visits.changeStatus(
                visitId, VisitStatus.DONE, null, new VisitService.Actor(payment.getRecordedBy(), StaffRole.CASHIER));
        payments.flush();
        recordAudit(AuditAction.PAYMENT_RECORDED, payment, invoice, PaymentStatus.PAID);
    }

    private void close(Payment payment, PaymentStatus outcome) {
        if (outcome == PaymentStatus.FAILED) {
            payment.markFailed();
        } else {
            payment.markUnconfirmed();
        }
        payments.flush();
        Invoice invoice = invoices.findById(payment.getInvoiceId()).orElseThrow();
        recordAudit(AuditAction.PAYMENT_RECORDED, payment, invoice, outcome);
    }

    /**
     * The cashier's finding on an UNCONFIRMED payment after checking the statement. The reason is
     * stored encrypted and the audit row records only the outcome.
     */
    public PaymentStatusResponse resolve(
            UUID visitId, boolean paid, @Nullable String receipt, String reason, VisitService.Actor actor) {
        transaction.executeWithoutResult(tx -> {
            visits.lockForChange(visitId);
            Invoice invoice = requireInvoice(visitId);
            Payment payment = payments.findByInvoiceIdAndStatus(invoice.getId(), PaymentStatus.UNCONFIRMED)
                    .orElseThrow(() -> new ConflictException("This invoice has no unconfirmed payment"));
            String typed = reason.strip();
            if (typed.length() < 10) {
                throw new BusinessRuleException("Give a reason of at least 10 characters");
            }
            if (paid && receipt == null) {
                throw new BusinessRuleException("Enter the M-Pesa receipt from the statement");
            }
            payment.resolved(typed, actor.staffId(), clock.instant());
            if (paid) {
                payment.markPaid(receipt);
                invoice.settle(InvoiceStatus.PAID);
                visits.changeStatus(visitId, VisitStatus.DONE, null, actor);
            } else {
                payment.markFailed();
            }
            payments.flush();
            recordAudit(AuditAction.PAYMENT_RESOLVED, payment, invoice, payment.getStatus());
        });
        return status(visitId);
    }

    /** Where the payment stands. Not access-logged: it says nothing about the patient's care. */
    public PaymentStatusResponse status(UUID visitId) {
        return Objects.requireNonNull(transaction.execute(tx -> {
            Invoice invoice = requireInvoice(visitId);
            return new PaymentStatusResponse(
                    invoice.getStatus(),
                    payments.findFirstByInvoiceIdOrderByIdDesc(invoice.getId())
                            .map(Payment::getStatus)
                            .orElse(null));
        }));
    }

    private void recordAudit(AuditAction action, Payment payment, Invoice invoice, PaymentStatus outcome) {
        audit.record(
                action,
                PAYMENT,
                payment.getId(),
                invoice.getPatientId(),
                invoice.getVisitId(),
                Map.of("method", payment.getMethod().name(), "status", outcome.name()));
    }

    private Payment requirePayment(UUID paymentId) {
        return payments.findById(paymentId).orElseThrow();
    }

    private Invoice requireInvoice(UUID visitId) {
        return invoices.findByVisitId(visitId).orElseThrow(() -> new NotFoundException("No invoice for this visit"));
    }
}
