import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse, mockApi, renderWithQuery } from '../../test/render';
import { SpokenInstructionsPanel } from './SpokenInstructionsPanel';
import type { SpokenInstructions } from './types';

const swahili: SpokenInstructions = {
  language: 'SWAHILI',
  version: 'instructions-v1',
  serverAudio: false,
  lines: [{ itemId: 'i1', text: 'Meza miligramu mia tano ya Amoxicillin.' }],
};
const english: SpokenInstructions = {
  ...swahili,
  language: 'ENGLISH',
  lines: [{ itemId: 'i1', text: 'Take five hundred milligrams of Amoxicillin.' }],
};

class FakeUtterance {
  voice: unknown = null;
  lang = '';
  onend: (() => void) | null = null;
  onerror: (() => void) | null = null;
  readonly text: string;
  constructor(text: string) {
    this.text = text;
  }
}

function installVoices(langs: string[]) {
  const speak = vi.fn((utterance: FakeUtterance) => {
    utterance.onend?.();
  });
  vi.stubGlobal('SpeechSynthesisUtterance', FakeUtterance);
  Object.defineProperty(window, 'speechSynthesis', {
    configurable: true,
    value: {
      getVoices: () => langs.map((lang) => ({ lang })),
      speak,
      cancel: vi.fn(),
      addEventListener: vi.fn(),
    },
  });
  return speak;
}

describe('SpokenInstructionsPanel', () => {
  let answer: SpokenInstructions;
  let api: ReturnType<typeof mockApi>;

  beforeEach(() => {
    answer = swahili;
    api = mockApi({
      'GET /api/visits/v1/prescription/instructions': () => jsonResponse(200, answer),
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    Reflect.deleteProperty(window, 'speechSynthesis');
  });

  it('speaks the same words it shows, with a voice of that language', async () => {
    const user = userEvent.setup();
    const speak = installVoices(['en-GB', 'sw-KE']);
    renderWithQuery(<SpokenInstructionsPanel visitId="v1" prescriptionId="p1" />);

    expect(await screen.findByText(swahili.lines[0]?.text ?? '')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Play instructions' }));

    await waitFor(() => {
      expect(speak).toHaveBeenCalledOnce();
    });
    const spoken = speak.mock.calls[0]?.[0];
    expect(spoken?.text).toBe(swahili.lines[0]?.text);
    expect(spoken?.lang).toBe('sw-KE');
  });

  it('never reads Swahili with another language voice', async () => {
    const user = userEvent.setup();
    const speak = installVoices(['en-GB']);
    renderWithQuery(<SpokenInstructionsPanel visitId="v1" prescriptionId="p1" />);

    await user.click(await screen.findByRole('button', { name: 'Play instructions' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Read the instructions');
    expect(speak).not.toHaveBeenCalled();
  });

  it('changes language', async () => {
    const user = userEvent.setup();
    installVoices(['en-GB']);
    renderWithQuery(<SpokenInstructionsPanel visitId="v1" prescriptionId="p1" />);

    const language = await screen.findByLabelText('Language');
    answer = english;
    await user.selectOptions(language, 'ENGLISH');

    expect(await screen.findByText(english.lines[0]?.text ?? '')).toBeInTheDocument();
    expect(
      api.mock.calls.some(([url]) => typeof url === 'string' && url.endsWith('?language=ENGLISH')),
    ).toBe(true);
  });
});
