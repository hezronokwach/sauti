package ke.sauti.hms.medication.speech;

import ke.sauti.hms.patient.PreferredLanguage;

/** Turns instruction text into audio. When unavailable, the browser speaks the text instead. */
public interface TextToSpeech {

    /** False means the server has no voice, so the screen uses the browser's. */
    boolean available();

    /** The voice name; part of the audio cache key. */
    String voice(PreferredLanguage language);

    /** MP3 audio of the text. Throws if the provider fails. */
    byte[] synthesise(String text, PreferredLanguage language);
}
