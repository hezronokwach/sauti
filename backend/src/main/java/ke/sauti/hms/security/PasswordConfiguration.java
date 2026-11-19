package ke.sauti.hms.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Passwords are hashed with Argon2id at OWASP's minimum settings. Each hash records its
 * own settings, so raising them later still verifies older hashes.
 */
@Configuration(proxyBeanMethods = false)
public class PasswordConfiguration {

    static final int SALT_BYTES = 16;
    static final int HASH_BYTES = 32;
    static final int PARALLELISM = 1;
    static final int MEMORY_KIB = 19_456;
    static final int ITERATIONS = 2;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(SALT_BYTES, HASH_BYTES, PARALLELISM, MEMORY_KIB, ITERATIONS);
    }
}
