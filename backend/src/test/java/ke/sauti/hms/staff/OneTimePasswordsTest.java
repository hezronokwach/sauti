package ke.sauti.hms.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OneTimePasswordsTest {

    private final OneTimePasswords passwords = new OneTimePasswords();

    @Test
    void isLongEnoughForThePasswordRules() {
        assertThat(passwords.next()).hasSize(16).hasSizeGreaterThanOrEqualTo(Staff.MIN_PASSWORD_LENGTH);
    }

    @Test
    void leavesOutLookAlikeCharacters() {
        for (int i = 0; i < 200; i++) {
            assertThat(passwords.next()).doesNotContainPattern("[0O1lI]").matches("[A-Za-z2-9]+");
        }
    }

    @Test
    void isDifferentEachTime() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            seen.add(passwords.next());
        }
        assertThat(seen).hasSize(200);
    }
}
