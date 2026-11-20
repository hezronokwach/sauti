package ke.sauti.hms.security;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * The HTTP security setup.
 * <ul>
 *   <li>Public: the login and code steps, the health check and the error page; everything
 *       else needs a logged-in session. {@code /api/admin/**} needs ADMIN here, before
 *       the body is read; other role rules sit on the endpoints ({@code @PreAuthorize}).
 *   <li>The logged-in person lives in the session (PostgreSQL, through Spring Session).
 *       The session cookie flags and the 15-minute idle timeout are in application.yml;
 *       {@link SessionAgeLimitFilter} adds the 8-hour cap.
 *   <li>CSRF uses the single-page-app setting: a readable {@code XSRF-TOKEN} cookie the
 *       front end echoes in an {@code X-XSRF-TOKEN} header on every changing request.
 *   <li>401 and 403 from the filters get the usual {@code ApiError} body.
 *   <li>Security headers on every response.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfiguration {

    /** The API only serves JSON, so the policy allows nothing to load or frame it. */
    static final String CONTENT_SECURITY_POLICY =
            "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

    /** The microphone is for this site only (recording consultations); nothing else is allowed. */
    static final String PERMISSIONS_POLICY = "microphone=(self), camera=(), geolocation=(), payment=()";

    static final long HSTS_SECONDS = 365L * 24 * 60 * 60;

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
    }

    /**
     * Shared with {@link AuthController}, which replaces the token at login and logout
     * because the login flow is its own, not one of Spring's login filters.
     */
    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.path("/").secure(true).sameSite("Strict"));
        return repository;
    }

    /** Makes URL rules and {@code @PreAuthorize} publish denials, for {@link AccessDeniedAuditor}. */
    @Bean
    AuthorizationEventPublisher authorizationEventPublisher(ApplicationEventPublisher publisher) {
        return new SpringAuthorizationEventPublisher(publisher);
    }

    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            SecurityContextRepository contextRepository,
            CsrfTokenRepository csrfTokenRepository,
            Clock clock,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
            AccessDeniedAuditor accessDeniedAuditor) {
        SecurityErrorResponder errors = new SecurityErrorResponder(exceptionResolver, accessDeniedAuditor);
        http.authorizeHttpRequests(
                        requests -> requests.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/code")
                                .permitAll()
                                .requestMatchers("/actuator/health", "/actuator/health/**", "/error")
                                .permitAll()
                                // Checked before the request body is read, so a non-admin learns
                                // nothing from validation errors; @PreAuthorize repeats it.
                                .requestMatchers("/api/admin/**")
                                .hasRole("ADMIN")
                                .requestMatchers("/api/patients/*/allergies", "/api/patients/*/allergies/**")
                                .hasAnyRole("NURSE", "DOCTOR")
                                .requestMatchers(HttpMethod.POST, "/api/patients")
                                .hasRole("RECEPTIONIST")
                                .requestMatchers(HttpMethod.GET, "/api/patients", "/api/patients/*")
                                .hasAnyRole("RECEPTIONIST", "NURSE", "DOCTOR", "CASHIER")
                                .requestMatchers("/api/consent/**", "/api/visits/*/consents")
                                .hasAnyRole("RECEPTIONIST", "NURSE", "DOCTOR")
                                .requestMatchers(HttpMethod.POST, "/api/visits")
                                .hasRole("RECEPTIONIST")
                                .requestMatchers(HttpMethod.POST, "/api/visits/*/take")
                                .hasAnyRole("NURSE", "DOCTOR")
                                .requestMatchers("/api/visits", "/api/visits/**")
                                .hasAnyRole("RECEPTIONIST", "NURSE", "DOCTOR", "CASHIER")
                                .anyRequest()
                                .authenticated())
                .securityContext(context -> context.securityContextRepository(contextRepository))
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
                .addFilterAfter(new SessionAgeLimitFilter(clock), SecurityContextHolderFilter.class)
                .exceptionHandling(
                        handling -> handling.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .headers(headers -> headers.httpStrictTransportSecurity(
                                hsts -> hsts.maxAgeInSeconds(HSTS_SECONDS).includeSubDomains(true))
                        .frameOptions(frame -> frame.deny())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", PERMISSIONS_POLICY)))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
