package ke.sauti.hms.queue;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface QueueTicketRepository extends JpaRepository<QueueTicket, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from QueueTicket t where t.id = ?1")
    Optional<QueueTicket> lockById(UUID id);

    Optional<QueueTicket> findByTicketDateAndCode(LocalDate ticketDate, String code);

    List<QueueTicket> findByTicketDateAndStatusInOrderByArrivedAtAscNumberAsc(
            LocalDate ticketDate, Collection<TicketStatus> statuses);

    List<QueueTicket> findTop5ByTicketDateAndLaneNotAndCalledAtNotNullOrderByCalledAtDesc(
            LocalDate ticketDate, TicketLane lane);
}
