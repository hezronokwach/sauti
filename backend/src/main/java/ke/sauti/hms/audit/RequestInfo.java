package ke.sauti.hms.audit;

import jakarta.servlet.http.HttpServletRequest;
import ke.sauti.hms.common.web.RequestIds;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The current request's id and client address, or nulls outside a request (for
 * example in a background job). Behind a hosting proxy the client address needs
 * server.forward-headers-strategy set, which the deployment issue configures.
 */
record RequestInfo(String requestId, String ip) {

    static RequestInfo current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return new RequestInfo(RequestIds.current(request), request.getRemoteAddr());
        }
        return new RequestInfo(RequestIds.current(null), null);
    }
}
