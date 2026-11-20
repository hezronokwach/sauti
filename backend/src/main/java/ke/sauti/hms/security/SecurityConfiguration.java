package ke.sauti.hms.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * The HTTP security setup the login flow needs: the login and code steps and the health
 * check are public, everything else needs a logged-in session, and the logged-in person
 * is kept in the session (stored in PostgreSQL by Spring Session). CSRF protection is on.
 * <p>
 * Issue 13 adds the session timeouts and cookie flags, the single-page-app CSRF setting,
 * security headers, and ApiError bodies for the 401 and 403 written here.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, SecurityContextRepository contextRepository) {
        http.authorizeHttpRequests(
                        requests -> requests.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/code")
                                .permitAll()
                                .requestMatchers("/actuator/health", "/actuator/health/**", "/error")
                                .permitAll()
                                .anyRequest()
                                .authenticated())
                .securityContext(context -> context.securityContextRepository(contextRepository))
                .exceptionHandling(
                        errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
