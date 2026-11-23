package ke.sauti.hms.medication.speech;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Azure text to speech. Used only when {@code sauti.speech.provider=azure}.
 *
 * @param region the Azure region, for example southafricanorth
 * @param apiKey from {@code AZURE_SPEECH_KEY}
 * @param connectTimeout how long to wait for a connection
 * @param readTimeout how long to wait for the audio
 */
@ConfigurationProperties(prefix = "sauti.speech.azure")
public record AzureSpeechProperties(String region, String apiKey, Duration connectTimeout, Duration readTimeout) {}
