package ke.sauti.hms.medication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import ke.sauti.hms.capture.AudioStore;
import ke.sauti.hms.common.error.ServiceUnavailableException;
import ke.sauti.hms.medication.speech.FakeTextToSpeech;
import ke.sauti.hms.patient.PatientService;
import ke.sauti.hms.patient.PreferredLanguage;
import ke.sauti.hms.visit.AccessPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class SpokenInstructionsCacheTest {

    private static final String TEXT = "Take one tablet.";

    private final AudioStore store = mock(AudioStore.class);
    private final FakeTextToSpeech speech = new FakeTextToSpeech();
    private final SpokenInstructions spoken = new SpokenInstructions(
            mock(PrescriptionRepository.class),
            mock(PrescriptionItemRepository.class),
            mock(MedicineRepository.class),
            mock(PatientService.class),
            mock(AccessPolicy.class),
            speech,
            store,
            mock(PlatformTransactionManager.class));

    @Test
    void makesAudioOnceThenUsesTheCache() {
        String key = SpokenInstructions.cacheKey(TEXT, speech.voice(PreferredLanguage.ENGLISH));
        when(store.find(key)).thenReturn(Optional.empty());
        byte[] made = spoken.cachedAudio(TEXT, PreferredLanguage.ENGLISH);
        verify(store).put(key, made);

        when(store.find(key)).thenReturn(Optional.of(made));
        assertThat(spoken.cachedAudio(TEXT, PreferredLanguage.ENGLISH)).isEqualTo(made);
        assertThat(speech.calls()).isEqualTo(1);
    }

    @Test
    void theKeyChangesWithTextAndVoice() {
        String key = SpokenInstructions.cacheKey(TEXT, "voice-a");
        assertThat(key).startsWith("instructions/").doesNotContain("tablet");
        assertThat(SpokenInstructions.cacheKey(TEXT, "voice-b")).isNotEqualTo(key);
        assertThat(SpokenInstructions.cacheKey("Take two tablets.", "voice-a")).isNotEqualTo(key);
    }

    @Test
    void aStorageFailureIsAServiceFailureSoTheBrowserCanSpeak() {
        when(store.find(anyString())).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("storage down")).when(store).put(anyString(), any());

        assertThatThrownBy(() -> spoken.cachedAudio(TEXT, PreferredLanguage.SWAHILI))
                .isInstanceOf(ServiceUnavailableException.class);
        verify(store, never()).delete(anyString());
    }
}
