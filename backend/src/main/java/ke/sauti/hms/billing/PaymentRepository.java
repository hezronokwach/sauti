package ke.sauti.hms.billing;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByInvoiceIdAndStatusNot(UUID invoiceId, PaymentStatus status);

    Optional<Payment> findByInvoiceIdAndStatus(UUID invoiceId, PaymentStatus status);

    /** The newest attempt; ids are time-ordered. */
    Optional<Payment> findFirstByInvoiceIdOrderByIdDesc(UUID invoiceId);

    /** The visit a payment belongs to, without loading the payment before it is locked. */
    @Query("select i.visitId from Payment p, Invoice i where p.id = ?1 and i.id = p.invoiceId")
    Optional<UUID> findVisitId(UUID paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = ?1")
    Optional<Payment> lockById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.checkoutRequestId = ?1")
    Optional<Payment> lockByCheckoutRequestId(String checkoutRequestId);
}
