package ke.sauti.hms.medication.speech;

import ke.sauti.hms.patient.PreferredLanguage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/** Azure's text to speech REST API, with Kenyan Swahili and English voices. */
@Component
@ConditionalOnProperty(name = "sauti.speech.provider", havingValue = "azure")
@EnableConfigurationProperties(AzureSpeechProperties.class)
public class AzureTextToSpeech implements TextToSpeech {

    private static final MediaType SSML = MediaType.parseMediaType("application/ssml+xml");

    private final RestClient client;

    public AzureTextToSpeech(AzureSpeechProperties properties) {
        if (!StringUtils.hasText(properties.apiKey()) || !properties.region().matches("[a-z0-9]+")) {
            throw new IllegalStateException("AZURE_SPEECH_KEY and AZURE_SPEECH_REGION must be set for azure speech");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        this.client = RestClient.builder()
                .baseUrl("https://" + properties.region() + ".tts.speech.microsoft.com")
                .requestFactory(factory)
                .defaultHeader("Ocp-Apim-Subscription-Key", properties.apiKey())
                .defaultHeader("X-Microsoft-OutputFormat", "audio-24khz-48kbitrate-mono-mp3")
                .defaultHeader("User-Agent", "sauti-hms")
                .build();
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String voice(PreferredLanguage language) {
        return language == PreferredLanguage.SWAHILI ? "sw-KE-ZuriNeural" : "en-KE-AsiliaNeural";
    }

    @Override
    public byte[] synthesise(String text, PreferredLanguage language) {
        byte[] audio = client.post()
                .uri("/cognitiveservices/v1")
                .contentType(SSML)
                .body(ssml(text, voice(language), language))
                .retrieve()
                .body(byte[].class);
        if (audio == null || audio.length == 0) {
            throw new IllegalStateException("Azure returned no audio");
        }
        return audio;
    }

    /** The request body. The text is escaped so it can never change the markup. */
    static String ssml(String text, String voice, PreferredLanguage language) {
        String locale = language == PreferredLanguage.SWAHILI ? "sw-KE" : "en-KE";
        return "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" xml:lang=\"" + locale + "\">"
                + "<voice name=\"" + voice + "\">" + escape(text) + "</voice></speak>";
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
