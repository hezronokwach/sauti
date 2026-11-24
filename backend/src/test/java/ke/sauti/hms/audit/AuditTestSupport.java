package ke.sauti.hms.audit;

import java.util.UUID;
import ke.sauti.hms.common.web.RequestIdFilter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Sets up and clears the "logged-in person" and "current request" that audit rows read. */
final class AuditTestSupport {

    static final String REQUEST_ID = "0f8fad5b-d9cb-469f-a165-70867728950e";
    static final String CLIENT_IP = "10.20.30.40";

    record TestActor(UUID actorId, String actorRole) implements AuditActor {}

    private AuditTestSupport() {}

    static void logIn(UUID staffId, String role) {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(new TestActor(staffId, role), null, "ROLE_" + role));
    }

    static void inRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");
        request.setRemoteAddr(CLIENT_IP);
        request.setAttribute(RequestIdFilter.ATTRIBUTE, REQUEST_ID);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    static void clear() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }
}
