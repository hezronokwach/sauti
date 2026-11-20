package ke.sauti.hms.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Groq's chat model for the clinical AI. Used only when {@code sauti.ai.provider=groq}.
 *
 * @param baseUrl the OpenAI-compatible API root
 * @param apiKey from {@code GROQ_API_KEY}
 * @param model the chat model, pinned
 * @param connectTimeout how long to wait for a connection
 * @param readTimeout how long to wait for an answer (20 seconds)
 */
@ConfigurationProperties(prefix = "sauti.ai.groq")
public record AiProperties(
        String baseUrl, String apiKey, String model, Duration connectTimeout, Duration readTimeout) {}
