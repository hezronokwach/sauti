package ke.sauti.hms.billing.mpesa;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Safaricom Daraja. Used only when {@code sauti.mpesa.provider=daraja}.
 *
 * @param baseUrl the sandbox or production API root
 * @param consumerKey from {@code MPESA_CONSUMER_KEY}
 * @param consumerSecret from {@code MPESA_CONSUMER_SECRET}
 * @param shortcode the business number
 * @param passkey from {@code MPESA_PASSKEY}
 * @param callbackBaseUrl this app's public address
 * @param connectTimeout how long to wait for a connection
 * @param readTimeout how long to wait for an answer
 */
@ConfigurationProperties(prefix = "sauti.mpesa.daraja")
public record DarajaProperties(
        String baseUrl,
        String consumerKey,
        String consumerSecret,
        String shortcode,
        String passkey,
        String callbackBaseUrl,
        Duration connectTimeout,
        Duration readTimeout) {}
