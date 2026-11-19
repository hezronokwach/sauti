package ke.sauti.hms.common.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Errors that happen outside a controller, for example in a filter, end up on
 * /error. This replaces Spring Boot's default error page so they also return
 * an {@link ApiError} with the request id.
 */
@RestController
public class ApiErrorController extends BaseExceptionHandler implements ErrorController {

    @RequestMapping("/error")
    ResponseEntity<ApiError> error(HttpServletRequest request) {
        int status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code ? code : 500;
        Exception exception =
                request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Exception ex ? ex : null;
        return respond(ErrorCode.forStatus(status), null, exception, request);
    }
}
