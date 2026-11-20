package ke.sauti.hms.registry;

import java.util.Map;
import java.util.Optional;
import ke.sauti.hms.audit.AuditAction;
import ke.sauti.hms.audit.AuditService;
import ke.sauti.hms.common.error.ForbiddenException;
import ke.sauti.hms.common.error.ServiceUnavailableException;
import ke.sauti.hms.staff.StaffRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reception's registry lookup. A hit fills name, date of birth and sex; a miss or a registry
 * that does not answer falls back to typing them. Every lookup is audited, without the ID
 * number. The ID is always typed by reception, never taken from speech.
 */
@Service
// The audit row is kept when the registry is down, so the 503 does not roll it back.
@Transactional(noRollbackFor = ServiceUnavailableException.class)
public class RegistryService {

    private final ClientRegistry registry;
    private final AuditService audit;

    public RegistryService(ClientRegistry registry, AuditService audit) {
        this.registry = registry;
        this.audit = audit;
    }

    public Optional<ClientRegistry.Person> lookup(IdType idType, String idNumber, StaffRole role) {
        if (role != StaffRole.RECEPTIONIST) {
            throw new ForbiddenException("Registry lookups are made at reception");
        }
        Optional<ClientRegistry.Person> person;
        try {
            person = registry.search(idType, idNumber.strip());
        } catch (RegistryUnavailableException e) {
            audit.record(AuditAction.REGISTRY_LOOKUP, Map.of("idType", idType.name(), "result", "UNAVAILABLE"));
            throw new ServiceUnavailableException("The registry is not answering; enter the details by hand");
        }
        audit.record(
                AuditAction.REGISTRY_LOOKUP,
                Map.of("idType", idType.name(), "result", person.isPresent() ? "FOUND" : "NOT_FOUND"));
        return person;
    }
}
