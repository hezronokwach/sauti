package ke.sauti.hms.staff;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import ke.sauti.hms.audit.AuditActor;
import ke.sauti.hms.staff.dto.CreateStaffRequest;
import ke.sauti.hms.staff.dto.CreatedStaffResponse;
import ke.sauti.hms.staff.dto.StaffSummaryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Staff account administration. Admins only. */
@RestController
@RequestMapping("/api/admin/staff")
@PreAuthorize("hasRole('ADMIN')")
public class StaffAdminController {

    private final StaffAdminService service;

    public StaffAdminController(StaffAdminService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedStaffResponse create(@Valid @RequestBody CreateStaffRequest request) {
        StaffAdminService.CreatedStaff created = service.create(request);
        return new CreatedStaffResponse(StaffSummaryResponse.of(created.staff()), created.oneTimePassword());
    }

    @GetMapping
    public List<StaffSummaryResponse> list() {
        return service.list().stream().map(StaffSummaryResponse::of).toList();
    }

    @PostMapping("/{id}/disable")
    public StaffSummaryResponse disable(@PathVariable UUID id, @AuthenticationPrincipal AuditActor admin) {
        return StaffSummaryResponse.of(service.disable(id, admin.actorId()));
    }
}
