package ke.sauti.hms.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;

/** Reads the current request's id, set by {@link RequestIdFilter}. */
public final class RequestIds {

    private RequestIds() {}

    /**
     * The request's id from the logging context, or from the request attribute when
     * the logging context is already cleared (an error dispatch after the filter ran).
     * Null outside a request.
     */
    public static String current(HttpServletRequest request) {
        String fromMdc = MDC.get(RequestIdFilter.MDC_KEY);
        if (fromMdc != null) {
            return fromMdc;
        }
        Object fromRequest = request == null ? null : request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return fromRequest instanceof String id ? id : null;
    }
}
