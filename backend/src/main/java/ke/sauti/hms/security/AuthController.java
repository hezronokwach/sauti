package ke.sauti.hms.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.Clock;
import ke.sauti.hms.common.error.ErrorCode;
import ke.sauti.hms.security.dto.ChangePasswordRequest;
import ke.sauti.hms.security.dto.CurrentStaffResponse;
import ke.sauti.hms.security.dto.LoginCodeRequest;
import ke.sauti.hms.security.dto.LoginNextStepResponse;
import ke.sauti.hms.security.dto.LoginPasswordRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login in two steps (password, then authenticator code), who is logged in, the forced
 * password change, and logout. Only the code step logs anyone in.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final LoginService loginService;
    private final SecurityContextRepository contextRepository;
    private final Clock clock;

    public AuthController(LoginService loginService, SecurityContextRepository contextRepository, Clock clock) {
        this.loginService = loginService;
        this.contextRepository = contextRepository;
        this.clock = clock;
    }

    @PostMapping("/login")
    public LoginNextStepResponse login(@Valid @RequestBody LoginPasswordRequest body, HttpServletRequest request) {
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            existing.removeAttribute(PendingLogin.ATTRIBUTE);
        }

        PasswordStep step = loginService.checkPassword(body.email(), body.password());
        // A session is only created once the password is right, so failed attempts
        // do not fill the session table.
        return switch (step) {
            case PasswordStep.Failed failed -> throw new LoginException(ErrorCode.LOGIN_FAILED);
            case PasswordStep.CodeRequired codeRequired -> {
                request.getSession()
                        .setAttribute(
                                PendingLogin.ATTRIBUTE,
                                PendingLogin.startingAt(codeRequired.staffId(), false, clock.instant()));
                yield LoginNextStepResponse.askForCode();
            }
            case PasswordStep.SetupRequired setup -> {
                request.getSession()
                        .setAttribute(
                                PendingLogin.ATTRIBUTE,
                                PendingLogin.startingAt(setup.staffId(), true, clock.instant()));
                yield LoginNextStepResponse.askForSetup(setup.otpauthUri());
            }
        };
    }

    @PostMapping("/code")
    public CurrentStaffResponse code(
            @Valid @RequestBody LoginCodeRequest body, HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        PendingLogin pending = session == null ? null : (PendingLogin) session.getAttribute(PendingLogin.ATTRIBUTE);
        if (pending == null || pending.isExpired(clock.instant())) {
            if (session != null) {
                session.removeAttribute(PendingLogin.ATTRIBUTE);
            }
            throw new LoginException(ErrorCode.LOGIN_EXPIRED);
        }

        CodeStep step = loginService.checkCode(pending, body.code());
        return switch (step) {
            case CodeStep.Failed failed -> {
                if (failed.loginEnded()) {
                    session.removeAttribute(PendingLogin.ATTRIBUTE);
                }
                throw new LoginException(ErrorCode.LOGIN_FAILED);
            }
            case CodeStep.LoggedIn loggedIn -> {
                session.removeAttribute(PendingLogin.ATTRIBUTE);
                saveNewContext(request, response);
                yield CurrentStaffResponse.of(loggedIn.principal());
            }
        };
    }

    @GetMapping("/me")
    public CurrentStaffResponse me(@AuthenticationPrincipal StaffPrincipal principal) {
        return CurrentStaffResponse.of(principal);
    }

    @PostMapping("/password")
    public CurrentStaffResponse changePassword(
            @AuthenticationPrincipal StaffPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        StaffPrincipal updated = loginService.changePassword(principal, body.currentPassword(), body.newPassword());
        saveNewContext(request, response);
        return CurrentStaffResponse.of(updated);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @AuthenticationPrincipal StaffPrincipal principal,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        loginService.recordLogout(principal);
        SecurityContextLogoutHandler logout = new SecurityContextLogoutHandler();
        logout.setSecurityContextRepository(contextRepository);
        logout.logout(request, response, authentication);
    }

    /**
     * Gives the session a new id before storing the context, so a session id planted
     * before the change (session fixation) is worthless afterwards.
     */
    private void saveNewContext(HttpServletRequest request, HttpServletResponse response) {
        request.changeSessionId();
        contextRepository.saveContext(SecurityContextHolder.getContext(), request, response);
    }
}
