package ke.sauti.hms.capture;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import ke.sauti.hms.jobs.JobFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Transcription by Groq's Whisper ({@code POST /audio/transcriptions}) with segment
 * timestamps and Whisper's per-segment confidence numbers, which the filter and grades
 * need. RestClient is used directly for that reason.
 *
 * <p>Errors map onto the job engine: 429 waits for the provider's Retry-After; timeouts,
 * connection failures, 5xx, 401 and 403 are temporary (retried; a wrong key can be fixed
 * without losing jobs); any other 4xx (bad audio) is permanent.
 */
@Component
@ConditionalOnProperty(name = "sauti.transcription.provider", havingValue = "groq")
@EnableConfigurationProperties(GroqProperties.class)
public class GroqSpeechToText implements SpeechToText {

    /** Whisper's prompt limit is 224 tokens; this many characters stays safely below it. */
    static final int MAX_PROMPT_CHARS = 600;

    static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(20);

    private static final Logger log = LoggerFactory.getLogger(GroqSpeechToText.class);

    private final RestClient client;
    private final String model;

    public GroqSpeechToText(GroqProperties properties) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new IllegalStateException("GROQ_API_KEY is not set but sauti.transcription.provider is groq");
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
        log.info("Transcription uses Groq: recorded audio is sent to this outside service");
    }

    @Override
    public List<Segment> transcribe(byte[] audio, String mimeType, String languageHint, String prompt) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new NamedBytes(audio, "piece" + extension(mimeType)));
        form.add("model", model);
        form.add("response_format", "verbose_json");
        form.add("timestamp_granularities[]", "segment");
        form.add("temperature", "0");
        if (StringUtils.hasText(languageHint)) {
            form.add("language", languageHint);
        }
        if (StringUtils.hasText(prompt)) {
            form.add("prompt", lastChars(prompt, MAX_PROMPT_CHARS));
        }
        try {
            Transcription result = client.post()
                    .uri("/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .onStatus(status -> status.value() == 429, (request, response) -> {
                        throw new JobFailure.RetryAfter(
                                retryAfter(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)), "Groq rate limit");
                    })
                    .onStatus(
                            status -> status.is4xxClientError() && !isTemporary(status.value()),
                            (request, response) -> {
                                throw new JobFailure.Permanent("Groq refused the audio ("
                                        + response.getStatusCode().value() + ")");
                            })
                    .onStatus(status -> status.isError(), (request, response) -> {
                        throw new IllegalStateException("Groq is unavailable ("
                                + response.getStatusCode().value() + ")");
                    })
                    .body(Transcription.class);
            if (result == null || result.segments() == null) {
                return List.of();
            }
            return result.segments().stream()
                    .map(s -> new Segment(
                            s.text() == null ? "" : s.text().trim(),
                            (int) Math.round(s.start() * 1000),
                            (int) Math.round(s.end() * 1000),
                            s.avgLogprob(),
                            s.noSpeechProb(),
                            s.compressionRatio()))
                    .toList();
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("Groq did not answer in time", e);
        } catch (RestClientException e) {
            // A timeout while reading the answer, or an answer that could not be read.
            throw new IllegalStateException("Groq did not answer in time or sent an unreadable answer", e);
        }
    }

    private static boolean isTemporary(int status) {
        return status == 401 || status == 403 || status == 408;
    }

    static Duration retryAfter(String header) {
        if (header == null) {
            return DEFAULT_RETRY_AFTER;
        }
        try {
            return Duration.ofSeconds(Math.max(1, Long.parseLong(header.trim())));
        } catch (NumberFormatException e) {
            return DEFAULT_RETRY_AFTER;
        }
    }

    static String lastChars(String text, int max) {
        return text.length() <= max ? text : text.substring(text.length() - max);
    }

    private static String extension(String mimeType) {
        String type = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        if (type.contains("ogg")) {
            return ".ogg";
        }
        if (type.contains("mp4")) {
            return ".m4a";
        }
        if (type.contains("mpeg")) {
            return ".mp3";
        }
        if (type.contains("wav")) {
            return ".wav";
        }
        return ".webm";
    }

    /** Multipart file part with a file name, which the API needs to read the format. */
    private static final class NamedBytes extends ByteArrayResource {

        private final String name;

        NamedBytes(byte[] bytes, String name) {
            super(bytes);
            this.name = name;
        }

        @Override
        public String getFilename() {
            return name;
        }

        @Override
        public boolean equals(Object other) {
            return super.equals(other);
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }
    }

    /** The parts of Groq's verbose_json answer that are used. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Transcription(List<GroqSegment> segments) {}

    /** One segment: times in seconds, and Whisper's confidence numbers. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GroqSegment(
            double start,
            double end,
            String text,
            @JsonProperty("avg_logprob") double avgLogprob,
            @JsonProperty("no_speech_prob") double noSpeechProb,
            @JsonProperty("compression_ratio") double compressionRatio) {}
}
