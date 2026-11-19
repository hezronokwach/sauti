package ke.sauti.hms.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import ke.sauti.hms.common.web.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Every exception the handlers know about, sent through the real MVC stack.
 * Security filters are opened up here; their own 401/403 responses are tested in phase 1.
 */
@WebMvcTest(controllers = ErrorTestController.class)
@Import(ExceptionHandlersTest.OpenSecurity.class)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ExceptionHandlersTest {

    @Autowired
    private MockMvc mvc;

    @TestConfiguration(proxyBeanMethods = false)
    static class OpenSecurity {
        @Bean
        SecurityFilterChain openChain(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .csrf(csrf -> csrf.disable())
                    .build();
        }
    }

    // ---- ApiException subclasses -------------------------------------------------

    @Test
    void notFound() throws Exception {
        expectError(mvc.perform(get("/test/not-found")), 404, "NOT_FOUND")
                .andExpect(jsonPath("$.message").value("Patient not found"));
    }

    @Test
    void conflict() throws Exception {
        expectError(mvc.perform(get("/test/conflict")), 409, "CONFLICT")
                .andExpect(jsonPath("$.message").value("Already taken by another doctor"));
    }

    @Test
    void businessRule() throws Exception {
        expectError(mvc.perform(get("/test/business-rule")), 422, "BUSINESS_RULE_VIOLATED");
    }

    @Test
    void forbidden() throws Exception {
        expectError(mvc.perform(get("/test/forbidden")), 403, "FORBIDDEN");
    }

    @Test
    void rateLimitedSaysWhenToRetry() throws Exception {
        expectError(mvc.perform(get("/test/rate-limited")), 429, "RATE_LIMITED")
                .andExpect(header().string("Retry-After", "30"));
    }

    @Test
    void serviceUnavailable() throws Exception {
        expectError(mvc.perform(get("/test/unavailable")), 503, "SERVICE_UNAVAILABLE");
    }

    // ---- Validation and malformed requests ---------------------------------------

    @Test
    void invalidBodyListsEveryField() throws Exception {
        expectError(
                        mvc.perform(post("/test/validated")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\" \",\"count\":0}")),
                        400,
                        "VALIDATION_FAILED")
                .andExpect(jsonPath("$.fieldErrors", hasSize(2)))
                .andExpect(jsonPath("$.fieldErrors[*].field", containsInAnyOrder("name", "count")));
    }

    @Test
    void invalidQueryParameterIsAFieldError() throws Exception {
        expectError(mvc.perform(get("/test/positive").param("count", "0")), 400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.fieldErrors[0].field").value("count"));
    }

    @Test
    void malformedJson() throws Exception {
        expectError(
                        mvc.perform(post("/test/validated")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{not json")),
                        400,
                        "MALFORMED_REQUEST")
                .andExpect(jsonPath("$.fieldErrors", emptyIterable()));
    }

    @Test
    void wrongParameterType() throws Exception {
        expectError(mvc.perform(get("/test/number/abc")), 400, "MALFORMED_REQUEST")
                .andExpect(jsonPath("$.fieldErrors[0].field").value("value"));
    }

    @Test
    void missingParameter() throws Exception {
        expectError(mvc.perform(get("/test/required")), 400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.fieldErrors[0].field").value("q"));
    }

    // ---- Persistence -------------------------------------------------------------

    @Test
    void staleVersion() throws Exception {
        expectError(mvc.perform(get("/test/stale")), 409, "STALE_VERSION");
    }

    @Test
    void integrityViolationHidesTheConstraint() throws Exception {
        expectError(mvc.perform(get("/test/integrity")), 409, "CONFLICT")
                .andExpect(content().string(not(containsString(ErrorTestController.SECRET_DETAIL))));
    }

    // ---- Spring MVC --------------------------------------------------------------

    @Test
    void unknownPath() throws Exception {
        expectError(mvc.perform(get("/test/nothing-here")), 404, "NOT_FOUND");
    }

    @Test
    void wrongMethodListsAllowedOnes() throws Exception {
        expectError(mvc.perform(delete("/test/not-found")), 405, "METHOD_NOT_ALLOWED")
                .andExpect(header().string("Allow", containsString("GET")));
    }

    @Test
    void unsupportedContentType() throws Exception {
        expectError(
                mvc.perform(post("/test/validated")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello")),
                415,
                "UNSUPPORTED_MEDIA_TYPE");
    }

    // ---- Security ----------------------------------------------------------------

    @Test
    void accessDenied() throws Exception {
        expectError(mvc.perform(get("/test/access-denied")), 403, "FORBIDDEN")
                .andExpect(content().string(not(containsString(ErrorTestController.SECRET_DETAIL))));
    }

    @Test
    void unauthenticated() throws Exception {
        expectError(mvc.perform(get("/test/unauthenticated")), 401, "UNAUTHENTICATED")
                .andExpect(content().string(not(containsString(ErrorTestController.SECRET_DETAIL))));
    }

    // ---- Fallback ----------------------------------------------------------------

    @Test
    void unexpectedErrorHidesDetailsButLogsTheStackTrace(CapturedOutput output) throws Exception {
        expectError(mvc.perform(get("/test/boom")), 500, "INTERNAL_ERROR")
                .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_ERROR.defaultMessage()))
                .andExpect(content().string(not(containsString(ErrorTestController.SECRET_DETAIL))));

        assertThat(output).contains("IllegalStateException: " + ErrorTestController.SECRET_DETAIL);
    }

    // ---- Request id ----------------------------------------------------------------

    @Test
    void errorCarriesTheCallersRequestIdInHeaderBodyAndLog(CapturedOutput output) throws Exception {
        String requestId = UUID.randomUUID().toString();

        mvc.perform(get("/test/conflict").header(RequestIdFilter.HEADER, requestId))
                .andExpect(header().string(RequestIdFilter.HEADER, requestId))
                .andExpect(jsonPath("$.requestId").value(requestId));

        assertThat(output).contains("[" + requestId + "]").contains("409 CONFLICT GET /test/conflict");
    }

    @Test
    void clientErrorsAreLoggedWithoutAStackTrace(CapturedOutput output) throws Exception {
        mvc.perform(get("/test/not-found"));

        assertThat(output).contains("404 NOT_FOUND GET /test/not-found").doesNotContain("\tat ke.sauti");
    }

    /** Status, code and request id are the same for every error. */
    private ResultActions expectError(ResultActions result, int status, String code) throws Exception {
        String headerId = result.andReturn().getResponse().getHeader(RequestIdFilter.HEADER);
        assertThat(headerId).as("X-Request-Id header").isNotBlank();
        return result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.requestId").value(headerId));
    }
}
