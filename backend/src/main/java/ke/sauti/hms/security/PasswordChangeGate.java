package ke.sauti.hms.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ke.sauti.hms.common.error.ErrorCode;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Until a person with a one-time password chooses their own, every API call except the
 * {@code /api/auth} ones is refused with 403 {@code PASSWORD_CHANGE_REQUIRED}. Running in
 * Spring MVC means the refusal is a normal ApiError response.
 */
@Configuration(proxyBeanMethods = false)
public class PasswordChangeGate implements WebMvcConfigurer, HandlerInterceptor {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**").excludePathPatterns("/api/auth/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof StaffPrincipal principal
                && principal.mustChangePassword()) {
            throw new LoginException(ErrorCode.PASSWORD_CHANGE_REQUIRED);
        }
        return true;
    }
}
