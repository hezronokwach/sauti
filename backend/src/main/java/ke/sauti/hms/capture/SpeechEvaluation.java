package ke.sauti.hms.capture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The speech evaluation (issue 29). Only with the {@code eval} profile: pushes every recording in
 * {@code eval/recordings/} through the real transcriber and the same keep-or-drop filter as the
 * app, compares the kept text with its reference, writes a results file to {@code eval/results/},
 * then stops the application. How to run it is in {@code eval/README.md}.
 *
 * <pre>
 * recordings  sNN-pP-quiet.ext or sNN-pP-noisy.ext
 * reference   eval/references/sNN-pP.txt if it exists (what was really said), else the script
 *             eval/scripts/sNN-*.txt
 * prompt      the medicine glossary, unless sauti.eval.glossary=false
 * language    sauti.eval.language-hint (sw or en), or none
 * </pre>
 */
@Component
@Profile("eval")
public class SpeechEvaluation {

    private static final Logger log = LoggerFactory.getLogger(SpeechEvaluation.class);
    private static final Pattern RECORDING =
            Pattern.compile("(s\\d{2})-(p\\d)-(quiet|noisy)\\.(webm|ogg|mp3|m4a|wav)", Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> MIME_TYPES = Map.of(
            "webm", "audio/webm",
            "ogg", "audio/ogg",
            "mp3", "audio/mpeg",
            "m4a", "audio/mp4",
            "wav", "audio/wav");
    private static final int WORST = 5;

    private final SpeechToText speechToText;
    private final ConfigurableApplicationContext context;
    private final Clock clock;
    private final Path dir;
    private final String languageHint;
    private final boolean glossary;
    private final String provider;
    private final String model;

    public SpeechEvaluation(
            SpeechToText speechToText,
            ConfigurableApplicationContext context,
            Clock clock,
            @Value("${sauti.eval.dir:../eval}") String dir,
            @Value("${sauti.eval.language-hint:}") String languageHint,
            @Value("${sauti.eval.glossary:true}") boolean glossary,
            @Value("${sauti.transcription.provider}") String provider,
            @Value("${sauti.transcription.groq.model}") String model) {
        this.speechToText = speechToText;
        this.context = context;
        this.clock = clock;
        this.dir = Path.of(dir);
        this.languageHint = languageHint;
        this.glossary = glossary;
        this.provider = provider;
        this.model = model;
    }

    /**
     * One recording's outcome.
     *
     * @param error why it could not be measured, or null
     */
    record Row(
            String recording,
            WordErrorRate.Result result,
            int kept,
            int dropped,
            @Nullable String error) {}

    /** Runs once start-up has finished, then stops the application. */
    @EventListener(ApplicationReadyEvent.class)
    public void runAndStop() throws IOException {
        Path results = evaluate();
        log.info("Speech evaluation finished, results in {}", results.toAbsolutePath());
        // The evaluation is a one-off command, so the process ends with it.
        System.exit(SpringApplication.exit(context, () -> 0));
    }

    /** Measures every recording and writes the results file; returns its path. */
    Path evaluate() throws IOException {
        List<Row> rows = new ArrayList<>();
        for (Path recording : recordings()) {
            rows.add(measure(recording));
        }
        return write(rows);
    }

    private List<Path> recordings() throws IOException {
        Path folder = dir.resolve("recordings");
        if (!Files.isDirectory(folder)) {
            throw new IllegalStateException("No recordings folder at " + folder.toAbsolutePath());
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(
                            p -> RECORDING.matcher(p.getFileName().toString()).matches())
                    .sorted()
                    .toList();
        }
    }

    private Row measure(Path recording) {
        String name = recording.getFileName().toString();
        Matcher m = RECORDING.matcher(name);
        if (!m.matches()) {
            throw new IllegalArgumentException(name);
        }
        WordErrorRate.Result empty = new WordErrorRate.Result(0, 0, List.of());
        try {
            String reference = WordErrorRate.withoutSpeakers(reference(m.group(1), m.group(2)));
            String mime = MIME_TYPES.get(m.group(4).toLowerCase(Locale.ROOT));
            List<SpeechToText.Segment> heard = speechToText.transcribe(
                    Files.readAllBytes(recording),
                    mime,
                    languageHint.isBlank() ? null : languageHint,
                    glossary ? MedicineGlossary.promptLine() : "");
            List<TranscriptFilter.Kept> kept = TranscriptFilter.apply(heard, null);
            String text = String.join(
                    " ", kept.stream().map(k -> k.segment().text().trim()).toList());
            log.info("Measured {}", name);
            return new Row(name, WordErrorRate.compare(reference, text), kept.size(), heard.size() - kept.size(), null);
        } catch (Exception e) {
            log.warn("Could not measure {}: {}", name, e.getMessage());
            return new Row(name, empty, 0, 0, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private String reference(String script, String pair) throws IOException {
        Path override = dir.resolve("references").resolve(script + "-" + pair + ".txt");
        if (Files.exists(override)) {
            return Files.readString(override, StandardCharsets.UTF_8);
        }
        try (Stream<Path> files = Files.list(dir.resolve("scripts"))) {
            Path file = files.filter(p -> p.getFileName().toString().startsWith(script + "-"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No script for " + script));
            return Files.readString(file, StandardCharsets.UTF_8);
        }
    }

    private Path write(List<Row> rows) throws IOException {
        String stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")
                .withZone(ZoneId.of("Africa/Nairobi"))
                .format(clock.instant());
        String settings = model + (glossary ? "" : "-noglossary") + (languageHint.isBlank() ? "" : "-" + languageHint);
        Path file = dir.resolve("results").resolve(stamp + "-" + settings + ".md");
        Files.createDirectories(file.getParent());
        Files.writeString(file, report(rows, stamp), StandardCharsets.UTF_8);
        return file;
    }

    String report(List<Row> rows, String stamp) {
        StringBuilder out = new StringBuilder();
        out.append("# Speech evaluation ").append(stamp).append("\n\n");
        out.append("Transcriber: ").append(provider).append(", model ").append(model);
        out.append(", glossary ").append(glossary ? "on" : "off");
        out.append(", language hint ")
                .append(languageHint.isBlank() ? "none" : languageHint)
                .append("\n\n");

        List<Row> measured = rows.stream().filter(r -> r.error() == null).toList();
        int words = measured.stream().mapToInt(r -> r.result().referenceWords()).sum();
        int edits = measured.stream().mapToInt(r -> r.result().edits()).sum();
        double mean =
                measured.stream().mapToDouble(r -> r.result().rate()).average().orElse(0);
        out.append(String.format(
                Locale.ROOT,
                "Word error rate: %.1f%% over all words, %.1f%% average per recording (%d of %d recordings measured)%n%n",
                words == 0 ? 0 : 100.0 * edits / words,
                100 * mean,
                measured.size(),
                rows.size()));
        for (String condition : List.of("quiet", "noisy")) {
            List<Row> some = measured.stream()
                    .filter(r -> r.recording().contains("-" + condition + "."))
                    .toList();
            int w = some.stream().mapToInt(r -> r.result().referenceWords()).sum();
            int e = some.stream().mapToInt(r -> r.result().edits()).sum();
            out.append(String.format(
                    Locale.ROOT, "- %s: %.1f%% (%d recordings)%n", condition, w == 0 ? 0 : 100.0 * e / w, some.size()));
        }

        out.append("\n## Worst ").append(WORST).append("\n\n");
        measured.stream()
                .sorted(Comparator.comparingDouble((Row r) -> r.result().rate()).reversed())
                .limit(WORST)
                .forEach(r -> out.append(String.format(
                        Locale.ROOT,
                        "- %s: %.1f%%%n",
                        r.recording(),
                        100 * r.result().rate())));

        out.append("\n## Every recording\n\n");
        out.append("| Recording | Words | Edits | WER | Kept | Dropped | Note |\n");
        out.append("| --- | ---: | ---: | ---: | ---: | ---: | --- |\n");
        for (Row r : rows) {
            out.append(String.format(
                    Locale.ROOT,
                    "| %s | %d | %d | %.1f%% | %d | %d | %s |%n",
                    r.recording(),
                    r.result().referenceWords(),
                    r.result().edits(),
                    100 * r.result().rate(),
                    r.kept(),
                    r.dropped(),
                    r.error() == null ? "" : r.error().replace('|', '/')));
        }

        out.append("\n## Check by eye: numbers, medicines and negations\n\n");
        out.append("Said / heard; a dash means the word was missing.\n\n");
        boolean any = false;
        for (Row r : measured) {
            for (WordErrorRate.Difference d : r.result().differences()) {
                out.append("- ")
                        .append(r.recording())
                        .append(": ")
                        .append(d.said() == null ? "-" : d.said())
                        .append(" / ")
                        .append(d.heard() == null ? "-" : d.heard())
                        .append('\n');
                any = true;
            }
        }
        if (!any) {
            out.append("None.\n");
        }
        return out.toString();
    }
}
