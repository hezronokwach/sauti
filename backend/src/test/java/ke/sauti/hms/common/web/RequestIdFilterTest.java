package ke.sauti.hms.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/anything");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final AtomicReference<String> idSeenInsideChain = new AtomicReference<>();
    private final FilterChain recordingChain = (req, res) -> idSeenInsideChain.set(MDC.get(RequestIdFilter.MDC_KEY));

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void reusesAValidIncomingId() throws Exception {
        String incoming = UUID.randomUUID().toString();
        request.addHeader(RequestIdFilter.HEADER, incoming);

        filter.doFilter(request, response, recordingChain);

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(incoming);
        assertThat(idSeenInsideChain.get()).isEqualTo(incoming);
        assertThat(request.getAttribute(RequestIdFilter.ATTRIBUTE)).isEqualTo(incoming);
    }

    @Test
    void normalisesAnUppercaseIncomingId() throws Exception {
        String incoming = UUID.randomUUID().toString();
        request.addHeader(RequestIdFilter.HEADER, incoming.toUpperCase(Locale.ROOT));

        filter.doFilter(request, response, recordingChain);

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(incoming);
    }

    @Test
    void replacesAnInvalidIncomingId() throws Exception {
        String hostile = "abc\nFAKE LOG LINE";
        request.addHeader(RequestIdFilter.HEADER, hostile);

        filter.doFilter(request, response, recordingChain);

        String issued = response.getHeader(RequestIdFilter.HEADER);
        assertThat(issued).isNotEqualTo(hostile);
        assertThat(UUID.fromString(issued).toString()).isEqualTo(issued);
        assertThat(idSeenInsideChain.get()).isEqualTo(issued);
    }

    @Test
    void replacesANonCanonicalUuid() throws Exception {
        // UUID.fromString accepts this short form; the filter must not.
        request.addHeader(RequestIdFilter.HEADER, "1-1-1-1-1");

        filter.doFilter(request, response, recordingChain);

        assertThat(response.getHeader(RequestIdFilter.HEADER))
                .isNotEqualTo("1-1-1-1-1")
                .hasSize(36);
    }

    @Test
    void generatesAnIdWhenNoneIsSent() throws Exception {
        filter.doFilter(request, response, recordingChain);

        String issued = response.getHeader(RequestIdFilter.HEADER);
        assertThat(UUID.fromString(issued).toString()).isEqualTo(issued);
        assertThat(idSeenInsideChain.get()).isEqualTo(issued);
    }

    @Test
    void clearsTheLoggingContextAfterTheRequest() throws Exception {
        filter.doFilter(request, response, recordingChain);

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void clearsTheLoggingContextEvenWhenTheRequestFails() {
        FilterChain failingChain = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, failingChain))
                .isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isNotNull();
    }
}
