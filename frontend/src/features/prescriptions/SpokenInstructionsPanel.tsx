import { useEffect, useState } from 'react';
import { errorMessage } from '../../api/client';
import { useSpokenInstructions } from './api';
import { speakInstructions, stopSpeaking } from './speakInstructions';
import type { InstructionLanguage } from './types';

interface SpokenInstructionsPanelProps {
  visitId: string;
  prescriptionId: string;
}

/** What the patient hears, always shown as the same words on screen. Signed prescriptions only. */
export function SpokenInstructionsPanel({ visitId, prescriptionId }: SpokenInstructionsPanelProps) {
  const [chosen, setChosen] = useState<InstructionLanguage | null>(null);
  const [speaking, setSpeaking] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const instructions = useSpokenInstructions(visitId, prescriptionId, chosen);

  useEffect(() => stopSpeaking, []);

  if (instructions.isPending) return <p role="status">Preparing the instructions...</p>;
  if (instructions.error) return <p role="alert">{errorMessage(instructions.error)}</p>;
  const current = instructions.data;

  const play = async () => {
    setProblem(null);
    setSpeaking(true);
    const message = await speakInstructions(visitId, current);
    setProblem(message);
    setSpeaking(false);
  };

  return (
    <section aria-labelledby="instructions-heading">
      <h3 id="instructions-heading">Instructions for the patient</h3>
      <p className="quiet">Demonstration wording, not clinically reviewed.</p>
      <label htmlFor="instructions-language">Language</label>
      <select
        id="instructions-language"
        value={current.language}
        onChange={(event) => {
          stopSpeaking();
          setProblem(null);
          setChosen(event.target.value as InstructionLanguage);
        }}
      >
        <option value="SWAHILI">Swahili</option>
        <option value="ENGLISH">English</option>
      </select>
      <ol aria-label="Spoken instructions">
        {current.lines.map((line) => (
          <li key={line.itemId}>{line.text}</li>
        ))}
      </ol>
      {problem ? <p role="alert">{problem}</p> : null}
      <div className="actions">
        {speaking ? (
          <button
            type="button"
            className="secondary"
            onClick={() => {
              stopSpeaking();
            }}
          >
            Stop
          </button>
        ) : (
          <button
            type="button"
            onClick={() => {
              void play();
            }}
          >
            Play instructions
          </button>
        )}
      </div>
    </section>
  );
}
