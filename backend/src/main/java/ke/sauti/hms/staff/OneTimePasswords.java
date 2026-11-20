package ke.sauti.hms.staff;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * One-time passwords for new accounts: 16 random characters, leaving out look-alikes
 * (0 and O, 1, l and I) so they can be read out or copied by hand without mistakes.
 */
@Component
public class OneTimePasswords {

    static final int LENGTH = 16;
    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder password = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return password.toString();
    }
}
