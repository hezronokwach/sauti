package ke.sauti.hms.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.RequestDispatcher;
import ke.sauti.hms.common.web.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/** Errors raised outside controllers (for example in a filter) still return an ApiError. */
class ApiErrorControllerTest {

    private static final String REQUEST_ID = "0f8fad5b-d9cb-469f-a165-70867728950e";

    private final ApiErrorController controller = new ApiErrorController();

    @Test
    void uncaughtFilterExceptionBecomesInternalError() {
        MockHttpServletRequest request = errorDispatch(500);
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("secret"));

        ResponseEntity<ApiError> response = controller.error(request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).doesNotContain("secret");
        assertThat(response.getBody().requestId()).isEqualTo(REQUEST_ID);
        assertThat(response.getHeaders().getFirst(RequestIdFilter.HEADER)).isEqualTo(REQUEST_ID);
    }

    @Test
    void containerStatusIsMappedToItsCode() {
        ResponseEntity<ApiError> response = controller.error(errorDispatch(404));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    void missingStatusIsTreatedAsInternalError() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");

        ResponseEntity<ApiError> response = controller.error(request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
    }

    private static MockHttpServletRequest errorDispatch(int status) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
        // The filter's finally block has already cleared the logging context by now.
        request.setAttribute(RequestIdFilter.ATTRIBUTE, REQUEST_ID);
        return request;
    }
}
