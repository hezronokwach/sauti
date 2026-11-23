package ke.sauti.hms.billing.mpesa;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Safaricom's Daraja API: a cached OAuth token, the STK push and the STK status query. The query
 * reports only how a prompt ended, so the amount and receipt come from the callback.
 */
@Component
@ConditionalOnProperty(name = "sauti.mpesa.provider", havingValue = "daraja")
@EnableConfigurationProperties(DarajaProperties.class)
public class DarajaMpesaGateway implements MpesaGateway {

    private static final JsonMapper JSON = JsonMapper.shared();
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Africa/Nairobi"));
    private static final Duration TOKEN_MARGIN = Duration.ofSeconds(60);
    private static final int MIN_SECRET_LENGTH = 32;
    private static final int MAX_REFERENCE = 12;
    /** Daraja's answer while the customer has not finished. */
    private static final String STILL_PROCESSING = "500.001.1001";
    /** Insufficient funds, cancelled, timed out, another transaction running, wrong PIN. */
    private static final Set<String> FAILED_CODES = Set.of("1", "1032", "1037", "1001", "2001");

    private final RestClient client;
    private final DarajaProperties properties;
    private final String callbackUrl;
    private final Clock clock;
    private String token = "";
    private Instant tokenExpires = Instant.EPOCH;

    public DarajaMpesaGateway(
            DarajaProperties properties, @Value("${sauti.mpesa.callback-secret:}") String callbackSecret, Clock clock) {
        if (!StringUtils.hasText(properties.consumerKey())
                || !StringUtils.hasText(properties.consumerSecret())
                || !StringUtils.hasText(properties.shortcode())
                || !StringUtils.hasText(properties.passkey())
                || !StringUtils.hasText(properties.callbackBaseUrl())
                || callbackSecret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "Daraja needs its keys, a public callback address and a callback secret of 32+ characters");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        this.client = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
        this.properties = properties;
        this.callbackUrl = properties.callbackBaseUrl() + "/api/public/mpesa/callback/" + callbackSecret;
        this.clock = clock;
    }

    @Override
    public String stkPush(String phone, BigDecimal amountKes, String reference) {
        String timestamp = TIMESTAMP.format(clock.instant());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("BusinessShortCode", properties.shortcode());
        body.put("Password", password(timestamp));
        body.put("Timestamp", timestamp);
        body.put("TransactionType", "CustomerPayBillOnline");
        body.put("Amount", amountKes.toBigIntegerExact());
        body.put("PartyA", phone);
        body.put("PartyB", properties.shortcode());
        body.put("PhoneNumber", phone);
        body.put("CallBackURL", callbackUrl);
        body.put("AccountReference", reference.substring(0, Math.min(reference.length(), MAX_REFERENCE)));
        body.put("TransactionDesc", "Hospital bill");
        try {
            JsonNode answer = post("/mpesa/stkpush/v1/processrequest", body);
            JsonNode id = answer.path("CheckoutRequestID");
            if (!"0".equals(answer.path("ResponseCode").asString("")) || !id.isString()) {
                throw new MpesaRejectedException("M-Pesa did not accept the prompt");
            }
            return id.asString();
        } catch (HttpClientErrorException e) {
            throw new MpesaRejectedException(
                    "M-Pesa refused the prompt: " + e.getStatusCode().value(), e);
        }
    }

    @Override
    public QueryResult query(String checkoutRequestId) {
        String timestamp = TIMESTAMP.format(clock.instant());
        Map<String, Object> body = Map.of(
                "BusinessShortCode",
                properties.shortcode(),
                "Password",
                password(timestamp),
                "Timestamp",
                timestamp,
                "CheckoutRequestID",
                checkoutRequestId);
        try {
            String code = post("/mpesa/stkpushquery/v1/query", body)
                    .path("ResultCode")
                    .asString("");
            return new QueryResult(outcome(code), null, null);
        } catch (HttpServerErrorException e) {
            if (e.getResponseBodyAsString().contains(STILL_PROCESSING)) {
                return new QueryResult(Outcome.PROCESSING, null, null);
            }
            throw e;
        }
    }

    /** An unknown code is never treated as failed: it ends with a person checking the statement. */
    static Outcome outcome(String code) {
        if ("0".equals(code)) {
            return Outcome.SUCCESS;
        }
        return FAILED_CODES.contains(code) ? Outcome.FAILED : Outcome.PROCESSING;
    }

    private JsonNode post(String path, Map<String, Object> body) {
        String answer = client.post()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        return JSON.readTree(answer == null ? "{}" : answer);
    }

    private String password(String timestamp) {
        String raw = properties.shortcode() + properties.passkey() + timestamp;
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** The OAuth token, fetched again a minute before it expires. */
    private synchronized String accessToken() {
        Instant now = clock.instant();
        if (now.isBefore(tokenExpires)) {
            return token;
        }
        String answer = client.get()
                .uri("/oauth/v1/generate?grant_type=client_credentials")
                .headers(headers -> headers.setBasicAuth(properties.consumerKey(), properties.consumerSecret()))
                .retrieve()
                .body(String.class);
        JsonNode parsed = JSON.readTree(answer == null ? "{}" : answer);
        JsonNode access = parsed.path("access_token");
        if (!access.isString()) {
            throw new IllegalStateException("Daraja returned no access token");
        }
        token = access.asString();
        long seconds = Long.parseLong(parsed.path("expires_in").asString("3599"));
        tokenExpires = now.plusSeconds(seconds).minus(TOKEN_MARGIN);
        return token;
    }
}
