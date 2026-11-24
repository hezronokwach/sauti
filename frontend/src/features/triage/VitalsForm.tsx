import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { ApiRequestError, errorMessage } from '../../api/client';
import { useRecordVitals } from './api';
import type { RecordVitalsRequest } from './types';

/**
 * A number typed into a text box, within plausible human limits (the same as the backend's).
 * Optional readings may be left blank.
 */
function reading(label: string, min: number, max: number, unit: string, optional = false) {
  const range = `${label} must be between ${String(min)} and ${String(max)} ${unit}`;
  const value = z
    .string()
    .trim()
    .transform((text) => (text === '' ? undefined : Number(text)))
    .refine((n) => n === undefined || Number.isFinite(n), `${label} must be a number`)
    .refine((n) => n === undefined || (n >= min && n <= max), range);
  return optional
    ? value
    : value.refine((n) => n !== undefined, `Enter the ${label.toLowerCase()}`);
}

const schema = z
  .object({
    temperatureC: reading('Temperature', 30, 45, '°C'),
    pulseBpm: reading('Pulse', 20, 250, 'beats a minute'),
    respiratoryRate: reading('Breathing rate', 4, 80, 'breaths a minute'),
    systolicMmhg: reading('Systolic pressure', 50, 260, 'mmHg'),
    diastolicMmhg: reading('Diastolic pressure', 20, 160, 'mmHg'),
    spo2Percent: reading('Oxygen saturation', 50, 100, '%'),
    weightKg: reading('Weight', 0.5, 300, 'kg', true),
    heightCm: reading('Height', 30, 250, 'cm', true),
  })
  .refine(
    (v) =>
      v.systolicMmhg === undefined ||
      v.diastolicMmhg === undefined ||
      v.systolicMmhg > v.diastolicMmhg,
    { path: ['diastolicMmhg'], message: 'Diastolic must be lower than systolic' },
  );

type FormInput = z.input<typeof schema>;
type FormOutput = z.output<typeof schema>;
type Field = keyof FormInput;

const WHOLE = new Set<Field>([
  'pulseBpm',
  'respiratoryRate',
  'systolicMmhg',
  'diastolicMmhg',
  'spo2Percent',
]);

const tenths = (n: number) => Math.round(n * 10) / 10;

function toRequest(v: FormOutput): RecordVitalsRequest {
  // The schema has already refused blanks in the required readings.
  const required = (n: number | undefined) => n ?? Number.NaN;
  return {
    temperatureC: tenths(required(v.temperatureC)),
    pulseBpm: Math.round(required(v.pulseBpm)),
    respiratoryRate: Math.round(required(v.respiratoryRate)),
    systolicMmhg: Math.round(required(v.systolicMmhg)),
    diastolicMmhg: Math.round(required(v.diastolicMmhg)),
    spo2Percent: Math.round(required(v.spo2Percent)),
    weightKg: v.weightKg === undefined ? undefined : tenths(v.weightKg),
    heightCm: v.heightCm === undefined ? undefined : tenths(v.heightCm),
  };
}

const LABELS: Record<Field, string> = {
  temperatureC: 'Temperature (°C)',
  pulseBpm: 'Pulse (beats a minute)',
  respiratoryRate: 'Breathing rate (breaths a minute)',
  systolicMmhg: 'Blood pressure, systolic (mmHg)',
  diastolicMmhg: 'Blood pressure, diastolic (mmHg)',
  spo2Percent: 'Oxygen saturation (%)',
  weightKg: 'Weight (kg, optional)',
  heightCm: 'Height (cm, optional)',
};

interface VitalsFormProps {
  visitId: string;
  /** Called once the vitals are saved and the patient is in the doctor's queue. */
  onRecorded: () => void;
}

/**
 * The nurse's vitals form. Values outside human limits are refused here and again by the
 * backend; saving sends the patient on to the doctor.
 */
export function VitalsForm({ visitId, onRecorded }: VitalsFormProps) {
  const form = useForm<FormInput, unknown, FormOutput>({ resolver: zodResolver(schema) });
  const record = useRecordVitals(visitId);
  const { errors } = form.formState;
  const serverFieldErrors = record.error instanceof ApiRequestError ? record.error.fieldErrors : [];

  const fieldError = (name: Field) =>
    errors[name]?.message ?? serverFieldErrors.find((e) => e.field === name)?.message;

  return (
    <section aria-labelledby="vitals-heading">
      <h2 id="vitals-heading">Vitals</h2>
      <form
        noValidate
        onSubmit={(event) => {
          void form.handleSubmit((values) => {
            // Awaited rather than an onSuccess callback: saving moves the visit on, which
            // replaces this form, and a replaced form's callbacks never run.
            return record.mutateAsync(toRequest(values)).then(onRecorded, () => undefined);
          })(event);
        }}
      >
        {(Object.keys(LABELS) as Field[]).map((name) => {
          const message = fieldError(name);
          return (
            <div key={name}>
              <label htmlFor={`vitals-${name}`}>{LABELS[name]}</label>
              <input
                id={`vitals-${name}`}
                inputMode={WHOLE.has(name) ? 'numeric' : 'decimal'}
                autoComplete="off"
                aria-invalid={message ? true : undefined}
                aria-describedby={message ? `vitals-${name}-error` : undefined}
                {...form.register(name)}
              />
              {message ? (
                <p id={`vitals-${name}-error`} className="field-error">
                  {message}
                </p>
              ) : null}
            </div>
          );
        })}
        {record.error && serverFieldErrors.length === 0 ? (
          <p role="alert">{errorMessage(record.error)}</p>
        ) : null}
        <button type="submit" disabled={record.isPending}>
          {record.isPending ? 'Saving...' : 'Save and send to doctor'}
        </button>
      </form>
    </section>
  );
}
