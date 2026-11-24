package ke.sauti.hms.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import ke.sauti.hms.jobs.JobFailure;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The Groq adapter against a local mock server: the request it sends and each error mapping. */
class GroqSpeechToTextTest {

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private volatile Responder responder;

    @FunctionalInterface
    interface Responder {
        void respond(HttpExchange exchange) throws IOException;
    }

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/openai/v1/audio/transcriptions", exchange -> {
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            responder.respond(exchange);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private GroqSpeechToText adapter(Duration readTimeout) {
        return new GroqSpeechToText(new GroqProperties(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1",
                "test-key",
                "whisper-large-v3",
                Duration.ofSeconds(2),
                readTimeout));
    }

    private static void reply(HttpExchange exchange, int status, String body, String... headers) throws IOException {
        for (int i = 0; i + 1 < headers.length; i += 2) {
            exchange.getResponseHeaders().add(headers[i], headers[i + 1]);
        }
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static final String VERBOSE_JSON = """
            {"task":"transcribe","language":"swahili","duration":30.0,"text":"Habari daktari. Sina homa.",
             "segments":[
               {"id":0,"start":0.0,"end":2.48,"text":" Habari daktari.","avg_logprob":-0.21,
                "no_speech_prob":0.012,"compression_ratio":1.31,"temperature":0.0},
               {"id":1,"start":2.48,"end":4.1,"text":" Sina homa.","avg_logprob":-0.47,
                "no_speech_prob":0.03,"compression_ratio":1.1,"temperature":0.0}]}
            """;

    @Test
    void sendsTheModelFormatTemperatureLanguagePromptAndAudio() throws Exception {
        responder = exchange -> reply(exchange, 200, VERBOSE_JSON);

        adapter(Duration.ofSeconds(5))
                .transcribe(
                        "OggS-audio".getBytes(StandardCharsets.UTF_8), "audio/ogg", "sw", "paracetamol, amoxicillin");

        assertThat(lastAuth.get()).isEqualTo("Bearer test-key");
        assertThat(lastBody.get())
                .contains("name=\"model\"", "whisper-large-v3")
                .contains("name=\"response_format\"", "verbose_json")
                .contains("name=\"timestamp_granularities[]\"", "segment")
                .contains("name=\"temperature\"")
                .contains("name=\"language\"", "sw")
                .contains("name=\"prompt\"", "paracetamol, amoxicillin")
                .contains("filename=\"piece.ogg\"", "OggS-audio");
    }

    @Test
    void mapsSegmentsWithTheirConfidenceNumbers() throws Exception {
        responder = exchange -> reply(exchange, 200, VERBOSE_JSON);

        List<SpeechToText.Segment> segments =
                adapter(Duration.ofSeconds(5)).transcribe(new byte[] {1}, "audio/webm", null, null);

        assertThat(segments)
                .containsExactly(
                        new SpeechToText.Segment("Habari daktari.", 0, 2_480, -0.21, 0.012, 1.31),
                        new SpeechToText.Segment("Sina homa.", 2_480, 4_100, -0.47, 0.03, 1.1));
        assertThat(lastBody.get()).doesNotContain("name=\"language\"").doesNotContain("name=\"prompt\"");
    }

    @Test
    void aRateLimitWaitsForTheProvidersRetryAfter() {
        responder = exchange -> reply(exchange, 429, "{\"error\":{\"message\":\"Rate limit\"}}", "Retry-After", "7");

        assertThatThrownBy(() -> adapter(Duration.ofSeconds(5)).transcribe(new byte[] {1}, "audio/webm", "sw", "p"))
                .isInstanceOfSatisfying(
                        JobFailure.RetryAfter.class,
                        e -> assertThat(e.getDelay()).isEqualTo(Duration.ofSeconds(7)));
    }

    @Test
    void aServerErrorIsTemporary() {
        responder = exchange -> reply(exchange, 503, "{}");

        assertThatThrownBy(() -> adapter(Duration.ofSeconds(5)).transcribe(new byte[] {1}, "audio/webm", "sw", "p"))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(JobFailure.class);
    }

    @Test
    void aTimeoutIsTemporary() {
        responder = exchange -> {
            try {
                Thread.sleep(1_500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            reply(exchange, 200, VERBOSE_JSON);
        };

        assertThatThrownBy(() -> adapter(Duration.ofMillis(300)).transcribe(new byte[] {1}, "audio/webm", "sw", "p"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("did not answer in time");
    }

    @Test
    void refusedAudioIsPermanent() {
        responder = exchange -> reply(exchange, 400, "{\"error\":{\"message\":\"could not decode audio\"}}");

        assertThatThrownBy(() -> adapter(Duration.ofSeconds(5)).transcribe(new byte[] {1}, "audio/webm", "sw", "p"))
                .isInstanceOf(JobFailure.Permanent.class);
    }

    @Test
    void aBadKeyIsRetriedRatherThanLosingTheJob() {
        responder = exchange -> reply(exchange, 401, "{}");

        assertThatThrownBy(() -> adapter(Duration.ofSeconds(5)).transcribe(new byte[] {1}, "audio/webm", "sw", "p"))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(JobFailure.class);
    }

    @Test
    void aMissingKeyStopsStartup() {
        assertThatThrownBy(() -> new GroqSpeechToText(
                        new GroqProperties("http://x", " ", "m", Duration.ofSeconds(1), Duration.ofSeconds(1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GROQ_API_KEY");
    }

    @Test
    void longPromptsKeepTheirEndAndRetryAfterFallsBack() {
        assertThat(GroqSpeechToText.lastChars("abcdef", 3)).isEqualTo("def");
        assertThat(GroqSpeechToText.retryAfter(null)).isEqualTo(GroqSpeechToText.DEFAULT_RETRY_AFTER);
        assertThat(GroqSpeechToText.retryAfter("soon")).isEqualTo(GroqSpeechToText.DEFAULT_RETRY_AFTER);
    }
}
