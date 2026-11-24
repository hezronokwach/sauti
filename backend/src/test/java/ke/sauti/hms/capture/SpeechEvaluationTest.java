package ke.sauti.hms.capture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.context.ConfigurableApplicationContext;

/** One run over a temporary eval folder, with a stand-in transcriber instead of Groq. */
class SpeechEvaluationTest {

    @TempDir
    Path dir;

    @Test
    void aRunMeasuresEveryRecordingAndWritesTheResults() throws Exception {
        Files.createDirectories(dir.resolve("scripts"));
        Files.createDirectories(dir.resolve("recordings"));
        Files.createDirectories(dir.resolve("references"));
        write("scripts/s01-test.txt", "A: Sina homa.\nB: No known allergies.\n");
        write("references/s01-p2.txt", "A: Sina homa kabisa.\nB: No known allergies.\n");
        write("recordings/s01-p1-quiet.webm", "audio");
        write("recordings/s01-p2-noisy.m4a", "audio");
        write("recordings/notes.txt", "not a recording");

        List<String> prompts = new ArrayList<>();
        List<String> mimeTypes = new ArrayList<>();
        SpeechToText transcriber = (audio, mimeType, languageHint, prompt) -> {
            prompts.add(prompt);
            mimeTypes.add(mimeType);
            return List.of(
                    new SpeechToText.Segment("Sina homa.", 0, 1000, -0.2, 0.01, 1.1),
                    new SpeechToText.Segment("Thanks for watching", 1000, 2000, -0.2, 0.01, 1.1),
                    new SpeechToText.Segment("Known allergies.", 2000, 3000, -0.2, 0.01, 1.1));
        };
        SpeechEvaluation evaluation = new SpeechEvaluation(
                transcriber,
                Mockito.mock(ConfigurableApplicationContext.class),
                Clock.fixed(Instant.parse("2026-11-24T09:00:00Z"), ZoneOffset.UTC),
                dir.toString(),
                "",
                true,
                "fake",
                "whisper-large-v3");

        Path results = evaluation.evaluate();
        String report = Files.readString(results, StandardCharsets.UTF_8);

        assertThat(results.getFileName()).hasToString("2026-11-24-1200-whisper-large-v3.md");
        assertThat(mimeTypes).containsExactly("audio/webm", "audio/mp4");
        assertThat(prompts).allSatisfy(p -> assertThat(p).contains("amoxicillin"));
        // s01-p1: 5 reference words, "no" missing; the phantom phrase was dropped, not counted.
        assertThat(report).contains("| s01-p1-quiet.webm | 5 | 1 | 20.0% | 2 | 1 |  |");
        // s01-p2 uses the reference file: 6 words, "kabisa" and "no" missing.
        assertThat(report).contains("| s01-p2-noisy.m4a | 6 | 2 | 33.3% | 2 | 1 |  |");
        assertThat(report).contains("- s01-p1-quiet.webm: no / -");
        assertThat(report).doesNotContain("notes.txt");
        assertThat(report).contains("- quiet: 20.0% (1 recordings)");
    }

    private void write(String path, String text) throws Exception {
        Files.writeString(dir.resolve(path), text, StandardCharsets.UTF_8);
    }
}
