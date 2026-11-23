package ke.sauti.hms.medication.speech;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import ke.sauti.hms.patient.PreferredLanguage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** A stand-in for tests: the "audio" is the text's bytes, and calls are counted to prove caching. */
@Component
@ConditionalOnProperty(name = "sauti.speech.provider", havingValue = "fake")
public class FakeTextToSpeech implements TextToSpeech {

    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String voice(PreferredLanguage language) {
        return "fake-" + language.name();
    }

    @Override
    public byte[] synthesise(String text, PreferredLanguage language) {
        calls.incrementAndGet();
        return text.getBytes(StandardCharsets.UTF_8);
    }

    public int calls() {
        return calls.get();
    }
}
