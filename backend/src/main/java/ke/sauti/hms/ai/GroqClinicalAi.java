package ke.sauti.hms.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * The clinical AI on Groq's OpenAI-compatible chat API: the pinned model, temperature 0,
 * JSON output and the versioned prompt from {@code prompts/summary-v1.txt}. Any failure
 * (timeout, HTTP error, unreadable answer) is thrown for the caller to retry once.
 */
@Component
@ConditionalOnProperty(name = "sauti.ai.provider", havingValue = "groq")
@EnableConfigurationProperties(AiProperties.class)
public class GroqClinicalAi implements ClinicalAi {

    static final String PROMPT_VERSION = "summary-v1";
    private static final JsonMapper JSON = JsonMapper.shared();

    private final RestClient client;
    private final String model;
    private final String prompt;

    public GroqClinicalAi(AiProperties properties) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new IllegalStateException("GROQ_API_KEY is not set but sauti.ai.provider is groq");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        this.client = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .build();
        this.model = properties.model();
        this.prompt = loadPrompt();
    }

    static String loadPrompt() {
        try {
            return new ClassPathResource("prompts/" + PROMPT_VERSION + ".txt")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("The summary prompt is missing", e);
        }
    }

    @Override
    public List<DraftClaim> summarise(String transcript) {
        Map<String, Object> body = Map.of(
                "model",
                model,
                "temperature",
                0,
                "response_format",
                Map.of("type", "json_object"),
                "messages",
                List.of(
                        Map.of("role", "system", "content", prompt),
                        Map.of("role", "user", "content", "Transcript:\n" + transcript)));
        Completion completion = client.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Completion.class);
        if (completion == null
                || completion.choices() == null
                || completion.choices().isEmpty()) {
            throw new IllegalStateException("Groq sent no answer");
        }
        String content = completion.choices().getFirst().message().content();
        Claims claims = JSON.readValue(content, Claims.class);
        return claims.claims() == null
                ? List.of()
                : claims.claims().stream()
                        .filter(c -> c.text() != null && c.quote() != null)
                        .toList();
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public String promptVersion() {
        return PROMPT_VERSION;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Completion(List<Choice> choices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Claims(List<DraftClaim> claims) {}
}
