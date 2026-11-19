package ke.sauti.hms.common.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints that fail in every way the handlers must cover. */
@RestController
@RequestMapping("/test")
class ErrorTestController {

    static final String SECRET_DETAIL = "internal-detail-that-must-not-leak";

    record Body(@NotBlank String name, @Min(1) int count) {}

    @GetMapping("/not-found")
    void notFound() {
        throw new NotFoundException("Patient not found");
    }

    @GetMapping("/conflict")
    void conflict() {
        throw new ConflictException("Already taken by another doctor");
    }

    @GetMapping("/business-rule")
    void businessRule() {
        throw new BusinessRuleException("Checksum does not match");
    }

    @GetMapping("/forbidden")
    void forbidden() {
        throw new ForbiddenException("Invalid authenticator code");
    }

    @GetMapping("/rate-limited")
    void rateLimited() {
        throw new RateLimitedException("Too many login attempts", Duration.ofSeconds(30));
    }

    @GetMapping("/unavailable")
    void unavailable() {
        throw new ServiceUnavailableException("Transcription is unavailable");
    }

    @PostMapping("/validated")
    Body validated(@Valid @RequestBody Body body) {
        return body;
    }

    @GetMapping("/number/{value}")
    int number(@PathVariable int value) {
        return value;
    }

    @GetMapping("/required")
    String required(@RequestParam String q) {
        return q;
    }

    @GetMapping("/positive")
    int positive(@RequestParam @Min(1) int count) {
        return count;
    }

    @GetMapping("/stale")
    void stale() {
        throw new ObjectOptimisticLockingFailureException(Object.class, "some-id");
    }

    @GetMapping("/integrity")
    void integrity() {
        throw new DataIntegrityViolationException("duplicate key violates " + SECRET_DETAIL);
    }

    @GetMapping("/access-denied")
    void accessDenied() {
        throw new AccessDeniedException(SECRET_DETAIL);
    }

    @GetMapping("/unauthenticated")
    void unauthenticated() {
        throw new BadCredentialsException(SECRET_DETAIL);
    }

    @GetMapping("/boom")
    void boom() {
        throw new IllegalStateException(SECRET_DETAIL);
    }
}
