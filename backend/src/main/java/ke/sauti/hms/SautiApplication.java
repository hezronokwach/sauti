package ke.sauti.hms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Staff log in through the application's own login flow, so Spring Boot's generated
 * in-memory user (and its logged password) is switched off.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SautiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SautiApplication.class, args);
    }
}
