import { toApiRequestError } from '../../api/client';
import type { InstructionLanguage, SpokenInstructions } from './types';

const LOCALES: Record<InstructionLanguage, string> = { SWAHILI: 'sw', ENGLISH: 'en' };
const VOICE_WAIT_MS = 1000;

/** Each play gets a number, so a stopped one never carries on to its next line. */
let activeRun = 0;
let player: HTMLAudioElement | null = null;
let finishCurrent: (() => void) | null = null;

export function stopSpeaking() {
  activeRun += 1;
  player?.pause();
  player = null;
  if ('speechSynthesis' in window) window.speechSynthesis.cancel();
  finishCurrent?.();
  finishCurrent = null;
}

async function playServerAudio(visitId: string, itemId: string, language: InstructionLanguage) {
  const response = await fetch(
    `/api/visits/${visitId}/prescription/instructions/${itemId}/audio?language=${language}`,
    { credentials: 'include' },
  );
  if (!response.ok) throw await toApiRequestError(response);
  const url = URL.createObjectURL(await response.blob());
  try {
    const audio = new Audio(url);
    player = audio;
    await new Promise<void>((resolve, reject) => {
      finishCurrent = resolve;
      audio.onended = () => {
        resolve();
      };
      audio.onerror = () => {
        reject(new Error('The audio could not be played'));
      };
      audio.play().catch(reject);
    });
  } finally {
    URL.revokeObjectURL(url);
  }
}

/** Browsers load their voice list late, so wait briefly when it is still empty. */
async function browserVoices(): Promise<SpeechSynthesisVoice[]> {
  const voices = window.speechSynthesis.getVoices();
  if (voices.length > 0) return voices;
  await new Promise<void>((resolve) => {
    const timer = window.setTimeout(resolve, VOICE_WAIT_MS);
    window.speechSynthesis.addEventListener(
      'voiceschanged',
      () => {
        window.clearTimeout(timer);
        resolve();
      },
      { once: true },
    );
  });
  return window.speechSynthesis.getVoices();
}

/**
 * Speaks with a browser voice of the right language. Returns false when there is none: an
 * English voice reading Swahili could make a dose impossible to understand.
 */
async function speakInBrowser(text: string, language: InstructionLanguage): Promise<boolean> {
  if (!('speechSynthesis' in window)) return false;
  const locale = LOCALES[language];
  const voice = (await browserVoices()).find((option) =>
    option.lang.toLowerCase().startsWith(locale),
  );
  if (!voice) return false;
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.voice = voice;
  utterance.lang = voice.lang;
  return new Promise<boolean>((resolve) => {
    finishCurrent = () => {
      resolve(true);
    };
    utterance.onend = () => {
      resolve(true);
    };
    utterance.onerror = (event) => {
      resolve(event.error === 'canceled' || event.error === 'interrupted');
    };
    window.speechSynthesis.speak(utterance);
  });
}

/** Plays every line in order. Resolves with a message to show, or null when all was spoken. */
export async function speakInstructions(
  visitId: string,
  instructions: SpokenInstructions,
): Promise<string | null> {
  stopSpeaking();
  const run = activeRun;
  for (const line of instructions.lines) {
    if (run !== activeRun) return null;
    const played =
      instructions.serverAudio &&
      (await playServerAudio(visitId, line.itemId, instructions.language).then(
        () => true,
        () => false,
      ));
    if (run !== activeRun) return null;
    if (!played && !(await speakInBrowser(line.text, instructions.language))) {
      return 'This device has no voice for this language. Read the instructions to the patient.';
    }
  }
  return null;
}
