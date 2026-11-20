package ke.sauti.hms;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Every migration runs on a real PostgreSQL and Hibernate's schema check accepts the result. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class MigrationsTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void everyMigrationSucceeded() {
        List<String> versions = jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);

        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13");
    }

    @Test
    void everyTableExists() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);

        assertThat(tables)
                .contains(
                        "facility_settings",
                        "staff",
                        "spring_session",
                        "spring_session_attributes",
                        "audit_event",
                        "access_log",
                        "patient",
                        "patient_allergy",
                        "visit",
                        "visit_status_history",
                        "care_assignment",
                        "queue_counter",
                        "consent",
                        "capture_session",
                        "audio_piece",
                        "transcript_segment",
                        "job",
                        "vitals",
                        "clinical_note",
                        "note_addendum",
                        "ai_request");
    }
}
