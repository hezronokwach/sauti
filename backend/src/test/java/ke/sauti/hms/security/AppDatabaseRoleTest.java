package ke.sauti.hms.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import ke.sauti.hms.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Runs SQL with the app's database rights, in transactions that always roll back. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AppDatabaseRoleTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private void asApp(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            status.setRollbackOnly();
            jdbc.execute("set local role sauti_app");
            work.run();
        });
    }

    private void refused(String sql) {
        assertThatThrownBy(() -> asApp(() -> jdbc.execute(sql)))
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageMatching("(?s).*(permission denied|must be owner).*");
    }

    @ParameterizedTest
    @ValueSource(strings = {"audit_event", "access_log"})
    void auditRowsCannotBeChangedOrRemoved(String table) {
        refused("update " + table + " set id = id");
        refused("delete from " + table);
        refused("truncate " + table);
    }

    @Test
    void auditRowsCanBeAddedAndRead() {
        asApp(() -> {
            UUID id = UUID.randomUUID();
            jdbc.update("insert into audit_event (id, occurred_at, action) values (?, now(), 'LOGIN_OK')", id);
            assertThat(jdbc.queryForObject("select count(*) from audit_event where id = ?", Integer.class, id))
                    .isEqualTo(1);
        });
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "create table intruder (id int)",
                "drop table patient",
                "alter table patient add column intruder int",
                "select * from flyway_schema_history"
            })
    void theSchemaCannotBeChanged(String sql) {
        refused(sql);
    }

    @Test
    void normalTablesStillWork() {
        asApp(() -> {
            jdbc.queryForObject("select count(*) from patient", Integer.class);
            jdbc.update("update job set updated_at = updated_at");
            jdbc.update("delete from spring_session where false");
        });
    }

    @Test
    void everyAppTableIsReachable() {
        Integer missing = jdbc.queryForObject("""
                select count(*) from pg_class c
                join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = 'public' and c.relkind = 'r'
                  and c.relname <> 'flyway_schema_history'
                  and not has_table_privilege('sauti_app', c.oid, 'select, insert')
                """, Integer.class);
        assertThat(missing).isZero();
    }
}
