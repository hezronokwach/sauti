package ke.sauti.hms.billing;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import ke.sauti.hms.common.error.NotFoundException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Where Safaricom reports a prompt's result. The long secret in the path keeps others out, and
 * what arrives is still only a claim: {@link MpesaService} confirms it before anything is paid.
 * The body is read as text so a malformed one can never produce an error that logs this path.
 */
@RestController
public class MpesaCallbackController {

    private static final Logger log = LoggerFactory.getLogger(MpesaCallbackController.class);
    private static final JsonMapper JSON = JsonMapper.shared();
    private static final int MAX_BODY = 8_192;
    private static final Map<String, Object> ACCEPTED = Map.of("ResultCode", 0, "ResultDesc", "Accepted");

    private final MpesaService mpesa;
    private final byte[] secret;

    public MpesaCallbackController(MpesaService mpesa, @Value("${sauti.mpesa.callback-secret:}") String secret) {
        this.mpesa = mpesa;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/api/public/mpesa/callback/{secret}")
    public Map<String, Object> callback(
            @PathVariable("secret") String given, @RequestBody(required = false) @Nullable String body) {
        if (secret.length == 0 || !MessageDigest.isEqual(secret, given.getBytes(StandardCharsets.UTF_8))) {
            throw new NotFoundException("Not found");
        }
        if (body != null && body.length() <= MAX_BODY) {
            read(body);
        }
        return ACCEPTED;
    }

    private void read(String body) {
        try {
            JsonNode callback = JSON.readTree(body).path("Body").path("stkCallback");
            JsonNode checkout = callback.path("CheckoutRequestID");
            if (!checkout.isString()) {
                return;
            }
            BigDecimal amount = null;
            String receipt = null;
            for (JsonNode item : callback.path("CallbackMetadata").path("Item")) {
                String name = item.path("Name").asString("");
                JsonNode value = item.path("Value");
                if ("Amount".equals(name) && value.isNumber()) {
                    amount = value.decimalValue();
                } else if ("MpesaReceiptNumber".equals(name) && value.isString()) {
                    receipt = value.asString();
                }
            }
            mpesa.onCallback(checkout.asString(), amount, receipt);
        } catch (JacksonException e) {
            log.warn("Ignored an unreadable M-Pesa callback");
        }
    }
}
