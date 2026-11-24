package ke.sauti.hms.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import ke.sauti.hms.common.ratelimit.RateLimits.Limit;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Which endpoints are limited. It runs after the security checks and before the request body is
 * read. Limits on an account named in the body (login) are applied in the controller.
 */
@Configuration(proxyBeanMethods = false)
public class RateLimitConfiguration implements WebMvcConfigurer {

    /** One limited endpoint. The account limit is null where nobody is logged in. */
    private record Rule(
            HttpMethod method,
            String path,
            Limit addressLimit,
            @Nullable Limit accountLimit) {}

    private static final List<Rule> RULES = List.of(
            new Rule(HttpMethod.POST, "/api/auth/login", Limit.LOGIN_ADDRESS, null),
            new Rule(HttpMethod.POST, "/api/auth/code", Limit.CODE_ADDRESS, null),
            new Rule(HttpMethod.PUT, "/api/captures/*/pieces/*", Limit.UPLOAD_ADDRESS, Limit.UPLOAD_ACCOUNT),
            new Rule(HttpMethod.POST, "/api/visits/*/summary", Limit.SUMMARY_ADDRESS, Limit.SUMMARY_ACCOUNT),
            new Rule(HttpMethod.GET, "/api/public/queue-board", Limit.QUEUE_BOARD_ADDRESS, null),
            new Rule(HttpMethod.POST, "/api/public/pre-registrations", Limit.PRE_REGISTRATION_ADDRESS, null),
            new Rule(HttpMethod.POST, "/api/public/mpesa/callback/*", Limit.MPESA_CALLBACK_ADDRESS, null));

    private final ObjectProvider<RateLimits> limits;

    /** A provider because web-layer test slices load this class without the limits bean. */
    public RateLimitConfiguration(ObjectProvider<RateLimits> limits) {
        this.limits = limits;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        RateLimits available = limits.getIfAvailable();
        if (available == null) {
            return;
        }
        for (Rule rule : RULES) {
            registry.addInterceptor(new HandlerInterceptor() {
                        @Override
                        public boolean preHandle(
                                HttpServletRequest request, HttpServletResponse response, Object handler) {
                            if (rule.method().matches(request.getMethod())) {
                                apply(available, rule, request);
                            }
                            return true;
                        }
                    })
                    .addPathPatterns(rule.path());
        }
    }

    private static void apply(RateLimits limits, Rule rule, HttpServletRequest request) {
        limits.check(rule.addressLimit(), request.getRemoteAddr());
        Authentication login = SecurityContextHolder.getContext().getAuthentication();
        if (rule.accountLimit() != null
                && login != null
                && login.isAuthenticated()
                && !(login instanceof AnonymousAuthenticationToken)) {
            limits.check(rule.accountLimit(), login.getName());
        }
    }
}
