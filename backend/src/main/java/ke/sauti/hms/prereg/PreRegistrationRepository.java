package ke.sauti.hms.prereg;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreRegistrationRepository extends JpaRepository<PreRegistration, UUID> {

    long countByPhoneHashAndTicketDate(byte[] phoneHash, LocalDate ticketDate);

    long countByIpHashAndCreatedAtAfter(byte[] ipHash, Instant since);

    Optional<PreRegistration> findByTicketId(UUID ticketId);
}
