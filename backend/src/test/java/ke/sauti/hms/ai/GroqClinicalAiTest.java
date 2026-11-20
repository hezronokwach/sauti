package ke.sauti.hms.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The Groq chat adapter against a local mock server: no API key or network needed. */
class GroqClinicalAiTest {

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private volatile Responder responder = exchange -> reply(exchange, 500, "{}");

    @FunctionalInterface
    interface Responder {
        void respond(HttpExchange exchange) throws IOException;
    }

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/openai/v1/chat/completions", exchange -> {
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            responder.respond(exchange);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private GroqClinicalAi adapter(Duration readTimeout) {
        return new GroqClinicalAi(new AiProperties(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1",
                "test-key",
                "openai/gpt-oss-120b",
                Duration.ofSeconds(2),
                readTimeout));
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static String completion(String content) {
        return JsonMapper.shared()
                .writeValueAsString(java.util.Map.of(
                        "choices",
                        java.util.List.of(java.util.Map.of("message", java.util.Map.of("content", content)))));
    }

    @Test
    void sendsThePinnedModelTemperatureZeroJsonModeAndThePrompt() {
        responder = exchange -> reply(exchange, 200, completion("{\"claims\":[]}"));

        adapter(Duration.ofSeconds(5)).summarise("Nina homa siku tatu.");

        JsonNode body = JsonMapper.shared().readTree(lastBody.get());
        assertThat(lastAuth.get()).isEqualTo("Bearer test-key");
        assertThat(body.get("model").asString()).isEqualTo("openai/gpt-oss-120b");
        assertThat(body.get("temperature").asInt()).isZero();
        assertThat(body.get("response_format").get("type").asString()).isEqualTo("json_object");
        assertThat(body.get("messages").get(0).get("content").asString()).contains("Sina homa");
        assertThat(body.get("messages").get(1).get("content").asString()).contains("Nina homa siku tatu.");
    }

    @Test
    void readsTheClaimsAndDropsIncompleteOnes() {
        responder = exchange -> reply(
                exchange,
                200,
                completion("{\"claims\":[{\"section\":\"complaint\",\"text\":\"Fever for three days\","
                        + "\"quote\":\"nina homa siku tatu\"},{\"section\":\"plan\",\"text\":\"No quote\"}]}"));

        assertThat(adapter(Duration.ofSeconds(5)).summarise("Nina homa siku tatu."))
                .containsExactly(new ClinicalAi.DraftClaim("complaint", "Fever for three days", "nina homa siku tatu"));
    }

    @Test
    void anUnreadableAnswerAServerErrorAndATimeoutAllThrow() {
        responder = exchange -> reply(exchange, 200, completion("not json"));
        assertThatThrownBy(() -> adapter(Duration.ofSeconds(5)).summarise("x")).isInstanceOf(RuntimeException.class);

        responder = exchange -> reply(exchange, 503, "{}");
        assertThatThrownBy(() -> adapter(Duration.ofSeconds(5)).summarise("x")).isInstanceOf(RuntimeException.class);

        responder = exchange -> {
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            reply(exchange, 200, completion("{\"claims\":[]}"));
        };
        assertThatThrownBy(() -> adapter(Duration.ofMillis(200)).summarise("x")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void aMissingKeyStopsStartup() {
        assertThatThrownBy(() -> new GroqClinicalAi(
                        new AiProperties("http://x", "", "m", Duration.ofSeconds(1), Duration.ofSeconds(1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GROQ_API_KEY");
    }
}
