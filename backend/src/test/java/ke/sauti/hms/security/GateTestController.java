package ke.sauti.hms.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ordinary API endpoints for tests: one any logged-in person may call, and one only an
 * admin may call, until real role-restricted endpoints exist.
 */
@RestController
class GateTestController {

    @GetMapping("/api/test/ping")
    String ping() {
        return "pong";
    }

    @GetMapping("/api/test/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    String adminOnly() {
        return "admin";
    }
}
