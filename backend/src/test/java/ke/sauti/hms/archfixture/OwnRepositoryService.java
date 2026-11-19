package ke.sauti.hms.archfixture;

import java.util.UUID;

/** Allowed: a service using its own feature's repository. */
public class OwnRepositoryService {

    private final FixtureRepository repository;

    public OwnRepositoryService(FixtureRepository repository) {
        this.repository = repository;
    }

    public boolean exists(UUID id) {
        return repository.findById(id).isPresent();
    }
}
