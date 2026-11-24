package ke.sauti.hms.security;

import static org.assertj.core.api.Assertions.assertThat;

import ke.sauti.hms.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sessions live in PostgreSQL, so a restarted application still knows who is logged in.
 * A brand-new session store reading the same database stands in for the restart.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SessionPersistenceTest {

    @Autowired
    private JdbcIndexedSessionRepository sessions;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /** Creates and saves a session through the public Session interface; returns its id. */
    private static <S extends Session> String saveSession(SessionRepository<S> repository, String key, String value) {
        S session = repository.createSession();
        session.setAttribute(key, value);
        repository.save(session);
        return session.getId();
    }

    @Test
    void aSavedSessionIsFoundByAFreshSessionStore() {
        String id = saveSession(sessions, "greeting", "habari");

        JdbcIndexedSessionRepository afterRestart =
                new JdbcIndexedSessionRepository(jdbc, new TransactionTemplate(transactionManager));
        Session found = afterRestart.findById(id);

        assertThat(found).isNotNull();
        assertThat(found.<String>getAttribute("greeting")).isEqualTo("habari");
        sessions.deleteById(id);
    }

    @Test
    void sessionRowsAreStoredInTheSpringSessionTables() {
        String id = saveSession(sessions, "greeting", "habari");

        Long rows = jdbc.queryForObject("select count(*) from spring_session where session_id = ?", Long.class, id);
        Long attributes = jdbc.queryForObject(
                "select count(*) from spring_session_attributes a join spring_session s"
                        + " on a.session_primary_id = s.primary_id where s.session_id = ?",
                Long.class,
                id);

        assertThat(rows).isEqualTo(1);
        assertThat(attributes).isEqualTo(1);
        sessions.deleteById(id);
    }
}
