package ke.sauti.hms.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordConfigurationTest {

    private final PasswordEncoder encoder = new PasswordConfiguration().passwordEncoder();

    @Test
    void usesArgon2idAtOwaspMinimumSettings() {
        String hash = encoder.encode("Habari-yako-2026");

        assertThat(hash).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
    }

    @Test
    void matchesOnlyTheRightPassword() {
        String hash = encoder.encode("Habari-yako-2026");

        assertThat(encoder.matches("Habari-yako-2026", hash)).isTrue();
        assertThat(encoder.matches("habari-yako-2026", hash)).isFalse();
    }

    @Test
    void theSamePasswordHashesDifferentlyEachTime() {
        assertThat(encoder.encode("Habari-yako-2026")).isNotEqualTo(encoder.encode("Habari-yako-2026"));
    }
}
