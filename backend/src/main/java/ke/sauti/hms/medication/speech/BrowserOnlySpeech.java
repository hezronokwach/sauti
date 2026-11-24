package ke.sauti.hms.medication.speech;

import ke.sauti.hms.patient.PreferredLanguage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** The default until Azure is configured: the server sends text and the browser speaks it. */
@Component
@ConditionalOnProperty(name = "sauti.speech.provider", havingValue = "browser", matchIfMissing = true)
public class BrowserOnlySpeech implements TextToSpeech {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public String voice(PreferredLanguage language) {
        return "browser";
    }

    @Override
    public byte[] synthesise(String text, PreferredLanguage language) {
        throw new IllegalStateException("No server voice is configured");
    }
}
