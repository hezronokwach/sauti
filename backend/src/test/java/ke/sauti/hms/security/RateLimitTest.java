package ke.sauti.hms.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.common.error.RateLimitedException;
import ke.sauti.hms.common.ratelimit.RateLimits;
import ke.sauti.hms.common.ratelimit.RateLimits.Limit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Limits answer 429 in the standard error shape and count each address and account apart. */
@SpringBootTest(properties = "sauti.rate-limit.enabled=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RateLimitTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RateLimits limits;

    private ResultActions login(String email, String address) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Wrong-Password-123\"}")
                .with(request -> {
                    request.setRemoteAddr(address);
                    return request;
                })
                .with(csrf()));
    }

    private ResultActions preRegister(String address) throws Exception {
        return mvc.perform(post("/api/public/pre-registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(request -> {
                    request.setRemoteAddr(address);
                    return request;
                })
                .with(csrf()));
    }

    @Test
    void tooManyLoginsForOneAccountAreRefusedInTheErrorShape() throws Exception {
        String email = "limit-" + UUID.randomUUID() + "@sauti.test";
        for (long i = 0; i < Limit.LOGIN_ACCOUNT.capacity(); i++) {
            login(email, "10.1.0." + (i + 1)).andExpect(status().isUnauthorized());
        }

        login(email.toUpperCase(java.util.Locale.ROOT), "10.1.1.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.requestId").exists());
        login("other-" + email, "10.1.1.2").andExpect(status().isUnauthorized());
    }

    @Test
    void aPublicEndpointIsLimitedPerAddress() throws Exception {
        for (long i = 0; i < Limit.PRE_REGISTRATION_ADDRESS.capacity(); i++) {
            preRegister("10.2.0.1").andExpect(status().isBadRequest());
        }

        preRegister("10.2.0.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        preRegister("10.2.0.2").andExpect(status().isBadRequest());
    }

    @Test
    void onlyWrongSigningCodesUseUpTheirLimit() {
        String account = UUID.randomUUID().toString();
        for (long i = 0; i < Limit.WRONG_SIGNING_CODE.capacity() * 2; i++) {
            limits.requireAvailable(Limit.WRONG_SIGNING_CODE, account);
        }
        for (long i = 0; i < Limit.WRONG_SIGNING_CODE.capacity(); i++) {
            limits.requireAvailable(Limit.WRONG_SIGNING_CODE, account);
            limits.recordFailure(Limit.WRONG_SIGNING_CODE, account);
        }

        Assertions.assertThatThrownBy(() -> limits.requireAvailable(Limit.WRONG_SIGNING_CODE, account))
                .isInstanceOf(RateLimitedException.class);
        assertThat(account).isNotBlank();
    }
}
