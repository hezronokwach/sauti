package ke.sauti.hms.staff;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import ke.sauti.hms.audit.AuditAction;
import ke.sauti.hms.audit.AuditService;
import ke.sauti.hms.common.error.BusinessRuleException;
import ke.sauti.hms.common.error.ConflictException;
import ke.sauti.hms.common.error.NotFoundException;
import ke.sauti.hms.staff.dto.CreateStaffRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** What an admin does with staff accounts: create, list and disable. */
@Service
@Transactional
public class StaffAdminService {

    private static final String STAFF = Staff.class.getSimpleName();

    private final StaffRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final OneTimePasswords oneTimePasswords;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    public StaffAdminService(
            StaffRepository repository,
            PasswordEncoder passwordEncoder,
            OneTimePasswords oneTimePasswords,
            AuditService audit,
            ApplicationEventPublisher events) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.oneTimePasswords = oneTimePasswords;
        this.audit = audit;
        this.events = events;
    }

    /**
     * A new account with a one-time password.
     *
     * @param staff the saved account
     * @param oneTimePassword the password in plain text, to hand over once
     */
    public record CreatedStaff(Staff staff, String oneTimePassword) {}

    public CreatedStaff create(CreateStaffRequest request) {
        String licence = blankToNull(request.licenceNumber());
        if (request.role().requiresLicence() && licence == null) {
            throw new BusinessRuleException("A licence number is required for doctors and nurses");
        }
        if (repository.findByEmailIgnoreCase(request.email()).isPresent()) {
            throw new ConflictException("A staff member with this email already exists");
        }
        String oneTimePassword = oneTimePasswords.next();
        Staff staff = repository.save(new Staff(
                request.fullName().trim(),
                request.email(),
                blankToNull(request.phone()),
                request.role(),
                licence,
                passwordEncoder.encode(oneTimePassword)));
        audit.record(
                AuditAction.STAFF_CREATED,
                STAFF,
                staff.getId(),
                null,
                null,
                Map.of("role", staff.getRole().name()));
        return new CreatedStaff(staff, oneTimePassword);
    }

    @Transactional(readOnly = true)
    public List<Staff> list() {
        return repository.findAll(Sort.by("fullName", "email"));
    }

    /**
     * Disables an account and, once this commits, ends all of the person's sessions.
     * Disabling an already disabled account changes nothing and writes no audit row.
     *
     * @param id the account to disable
     * @param adminId the admin doing it, who may not disable their own account
     */
    public Staff disable(UUID id, UUID adminId) {
        Staff staff = repository.findById(id).orElseThrow(() -> new NotFoundException("No such staff member"));
        if (staff.getId().equals(adminId)) {
            throw new BusinessRuleException("You cannot disable your own account");
        }
        if (!staff.isActive()) {
            return staff;
        }
        staff.disable();
        audit.record(AuditAction.STAFF_DISABLED, STAFF, staff.getId(), null, null, null);
        events.publishEvent(new StaffDisabledEvent(staff.getId()));
        return staff;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
