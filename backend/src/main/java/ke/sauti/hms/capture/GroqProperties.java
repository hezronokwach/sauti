package ke.sauti.hms.capture;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Groq's Whisper transcription. Used only when {@code sauti.transcription.provider=groq}.
 *
 * @param baseUrl the OpenAI-compatible API root
 * @param apiKey from {@code GROQ_API_KEY}
 * @param model the Whisper model
 * @param connectTimeout how long to wait for a connection
 * @param readTimeout how long to wait for a transcription
 */
@ConfigurationProperties(prefix = "sauti.transcription.groq")
public record GroqProperties(
        String baseUrl, String apiKey, String model, Duration connectTimeout, Duration readTimeout) {}
