package ke.sauti.hms.prereg;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import ke.sauti.hms.audit.AuditAction;
import ke.sauti.hms.audit.AuditService;
import ke.sauti.hms.common.crypto.KeyedHasher;
import ke.sauti.hms.common.error.BusinessRuleException;
import ke.sauti.hms.common.error.ForbiddenException;
import ke.sauti.hms.common.error.NotFoundException;
import ke.sauti.hms.common.error.RateLimitedException;
import ke.sauti.hms.common.time.FacilityTime;
import ke.sauti.hms.jobs.JobQueue;
import ke.sauti.hms.patient.KenyanPhone;
import ke.sauti.hms.patient.PreferredLanguage;
import ke.sauti.hms.prereg.dto.PreRegisterRequest;
import ke.sauti.hms.queue.QueueTicket;
import ke.sauti.hms.queue.QueueTicketService;
import ke.sauti.hms.staff.StaffRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Pre-registration from the public page.
 *
 * <ul>
 *   <li>The public side only ever saves: it never looks up a patient, and answers with the
 *       new P ticket's code and nothing else, so it cannot reveal anyone's data.
 *   <li>Limited to {@value #PER_PHONE_PER_DAY} per phone a day and {@value #PER_IP_PER_HOUR}
 *       per address an hour (429 beyond).
 *   <li>Reception opens an entry by its ticket code to pre-fill the registration form.
 *   <li>Every entry is deleted at the end of its day by a job.
 * </ul>
 */
@Service
@Transactional
public class PreRegistrationService {

    static final int PER_PHONE_PER_DAY = 3;
    static final int PER_IP_PER_HOUR = 10;
    static final int MAX_AGE_YEARS = 120;
    private static final String PRE_REGISTRATION = PreRegistration.class.getSimpleName();

    private final PreRegistrationRepository repository;
    private final QueueTicketService tickets;
    private final JobQueue jobs;
    private final KeyedHasher hasher;
    private final AuditService audit;
    private final FacilityTime facilityTime;
    private final Clock clock;

    public PreRegistrationService(
            PreRegistrationRepository repository,
            QueueTicketService tickets,
            JobQueue jobs,
            KeyedHasher hasher,
            AuditService audit,
            FacilityTime facilityTime,
            Clock clock) {
        this.repository = repository;
        this.tickets = tickets;
        this.jobs = jobs;
        this.hasher = hasher;
        this.audit = audit;
        this.facilityTime = facilityTime;
        this.clock = clock;
    }

    /** Saves the entry and returns the P ticket's code. */
    public String submit(PreRegisterRequest request, String remoteAddress) {
        LocalDate today = facilityTime.today();
        if (request.dateOfBirth().isAfter(today) || request.dateOfBirth().isBefore(today.minusYears(MAX_AGE_YEARS))) {
            throw new BusinessRuleException("Check the date of birth");
        }
        String phone = KenyanPhone.normalise(request.phone())
                .orElseThrow(() -> new BusinessRuleException("Use a Kenyan mobile number, for example 0712 345 678"));
        // Hashes of their own, so a pre-registration can never be matched to a patient record.
        byte[] phoneHash = hasher.hash("pre-registration-phone:" + phone);
        byte[] ipHash = hasher.hash("pre-registration-ip:" + remoteAddress);
        Instant now = clock.instant();
        if (repository.countByPhoneHashAndTicketDate(phoneHash, today) >= PER_PHONE_PER_DAY
                || repository.countByIpHashAndCreatedAtAfter(ipHash, now.minus(Duration.ofHours(1)))
                        >= PER_IP_PER_HOUR) {
            throw new RateLimitedException(
                    "Too many pre-registrations; please register at the desk", Duration.ofHours(1));
        }

        QueueTicket ticket = tickets.issuePreRegistered();
        PreRegistration saved = repository.save(new PreRegistration(
                ticket.getId(),
                today,
                new PreRegistration.Details(
                        request.fullName().strip(),
                        request.dateOfBirth(),
                        request.sex(),
                        phone,
                        StringUtils.hasText(request.reasonForVisit())
                                ? request.reasonForVisit().strip()
                                : null,
                        request.preferredLanguage() == null ? PreferredLanguage.SWAHILI : request.preferredLanguage()),
                phoneHash,
                ipHash));
        Instant endOfDay = today.plusDays(1).atStartOfDay(facilityTime.zone()).toInstant();
        jobs.enqueueAt(PreRegistrationExpiryHandler.TYPE, saved.getId(), endOfDay);
        return ticket.getCode();
    }

    /** Reception opens today's pre-registration by its ticket code; the read is audited. */
    public PreRegistration forDesk(String ticketCode, UUID staffId, StaffRole role) {
        if (role != StaffRole.RECEPTIONIST) {
            throw new ForbiddenException("Pre-registrations are opened at reception");
        }
        QueueTicket ticket = tickets.todayByCode(ticketCode)
                .orElseThrow(() -> new NotFoundException("No ticket with this code today"));
        PreRegistration entry = repository
                .findByTicketId(ticket.getId())
                .orElseThrow(() -> new NotFoundException("This ticket has no pre-registration"));
        audit.record(
                AuditAction.PRE_REGISTRATION_OPENED,
                PRE_REGISTRATION,
                entry.getId(),
                null,
                null,
                Map.of("ticket", ticket.getCode(), "staff", staffId));
        return entry;
    }
}
