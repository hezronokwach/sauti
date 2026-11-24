package ke.sauti.hms.archfixture;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Breaks "controllers do not use repositories". Abstract so the application's
 * component scan skips it.
 */
@RestController
public abstract class LeakyController {

    private final FixtureRepository repository;

    protected LeakyController(FixtureRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/fixture")
    public boolean exists() {
        return repository.findById(UUID.randomUUID()).isPresent();
    }
}
