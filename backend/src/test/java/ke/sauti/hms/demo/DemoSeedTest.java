package ke.sauti.hms.demo;

import static org.assertj.core.api.Assertions.assertThat;

import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.staff.StaffRepository;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.support.ActingAs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** The demo gets twenty made-up patients once, one of them allergic to penicillin. */
@SpringBootTest(properties = "sauti.demo.seed=true")
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class DemoSeedTest {

    @Autowired
    private DemoSeed seed;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    @Test
    void addsThePatientsOnceWithOnePenicillinAllergyAndNoPhones() {
        ActingAs.staff(staffRepository, StaffRole.ADMIN);

        seed.seed();
        seed.seed();

        assertThat(count("select count(*) from patient")).isEqualTo(20);
        assertThat(count("select count(*) from patient where phone is not null"))
                .isZero();
        assertThat(count("select count(*) from patient_allergy where status = 'ACTIVE'"
                        + " and substance_group = 'PENICILLIN'"))
                .isEqualTo(1);
    }
}
