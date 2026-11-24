import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { errorMessage } from '../../api/client';
import { saveItem, useMedicineSearch } from './api';
import {
  DOSE_UNITS,
  FREQUENCIES,
  type DoseUnit,
  type Frequency,
  type Medicine,
  type Prescription,
  type PrescriptionItem,
} from './types';

const wholeNumber = (max: number, message: string) =>
  z
    .string()
    .trim()
    .refine(
      (value) => !value || (/^\d+$/.test(value) && Number(value) >= 1 && Number(value) <= max),
      message,
    );

// Details may stay empty on a draft. Anything typed must be a sensible value.
const schema = z.object({
  doseValue: z
    .string()
    .trim()
    .refine(
      (value) => !value || (/^\d{1,7}(\.\d{1,3})?$/.test(value) && Number(value) > 0),
      'Enter a dose above 0, with at most 3 decimals',
    ),
  doseUnit: z.string(),
  frequency: z.string(),
  durationDays: wholeNumber(365, 'Enter 1 to 365 days'),
  route: z.string(),
  quantity: wholeNumber(100_000, 'Enter a whole number above 0'),
  instructions: z.string().trim().max(500, 'Keep this under 500 characters'),
});

type Form = z.infer<typeof schema>;

const label = (medicine: Medicine) => `${medicine.name} ${medicine.strength} (${medicine.form})`;

const numberOrUndefined = (value: string) => (value ? Number(value) : undefined);

interface PrescriptionItemFormProps {
  visitId: string;
  /** The revision on screen; null when there is no prescription yet. */
  revision: number | null;
  /** The line being changed; absent when adding one. */
  editing?: PrescriptionItem;
  onSaved: (next: Prescription) => void;
  /** Called when the save was refused, so the screen can reload. */
  onFailed: () => void;
  onCancel?: () => void;
}

/** Choosing a medicine and writing its dose, frequency, duration, route and quantity. */
export function PrescriptionItemForm({
  visitId,
  revision,
  editing,
  onSaved,
  onFailed,
  onCancel,
}: PrescriptionItemFormProps) {
  const [search, setSearch] = useState('');
  const [medicine, setMedicine] = useState<Medicine | null>(editing?.medicine ?? null);
  const results = useMedicineSearch(medicine ? '' : search);
  const form = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: {
      doseValue: editing?.doseValue?.toString() ?? '',
      doseUnit: editing?.doseUnit ?? '',
      frequency: editing?.frequency ?? '',
      durationDays: editing?.durationDays?.toString() ?? '',
      route: editing?.route ?? '',
      quantity: editing?.quantity?.toString() ?? '',
      instructions: editing?.instructions ?? '',
    },
  });
  const { errors } = form.formState;
  const save = useMutation({
    mutationFn: (values: Form) => {
      if (!medicine) throw new Error('Choose a medicine');
      return saveItem(visitId, editing?.id ?? null, {
        revision: revision ?? undefined,
        medicineId: medicine.id,
        doseValue: numberOrUndefined(values.doseValue),
        doseUnit: values.doseUnit ? (values.doseUnit as DoseUnit) : undefined,
        frequency: values.frequency ? (values.frequency as Frequency) : undefined,
        durationDays: numberOrUndefined(values.durationDays),
        route: values.route || undefined,
        quantity: numberOrUndefined(values.quantity),
        instructions: values.instructions || undefined,
      });
    },
    onSuccess: (next) => {
      onSaved(next);
      if (!editing) {
        form.reset();
        setMedicine(null);
        setSearch('');
      }
    },
    onError: onFailed,
  });

  const choose = (next: Medicine) => {
    setMedicine(next);
    form.setValue('route', (next.routes.length === 1 ? next.routes[0] : undefined) ?? '');
  };

  const fieldError = (name: keyof Form) =>
    errors[name] ? (
      <p id={`rx-${name}-error`} className="field-error">
        {errors[name].message}
      </p>
    ) : null;
  const field = (name: keyof Form) => ({
    id: `rx-${name}`,
    'aria-invalid': errors[name] ? true : undefined,
    'aria-describedby': errors[name] ? `rx-${name}-error` : undefined,
    ...form.register(name),
  });

  if (!medicine) {
    return (
      <div>
        <label htmlFor="rx-search">Find a medicine</label>
        <input
          id="rx-search"
          type="search"
          autoComplete="off"
          value={search}
          onChange={(event) => {
            setSearch(event.target.value);
          }}
        />
        {results.error ? <p role="alert">{errorMessage(results.error)}</p> : null}
        {results.data?.length === 0 ? <p>No medicine on the list matches that.</p> : null}
        {results.data && results.data.length > 0 ? (
          <ul aria-label="Matching medicines">
            {results.data.map((option) => (
              <li key={option.id}>
                <button
                  type="button"
                  className="secondary"
                  onClick={() => {
                    choose(option);
                  }}
                >
                  {label(option)}
                </button>
              </li>
            ))}
          </ul>
        ) : null}
      </div>
    );
  }

  return (
    <form
      noValidate
      aria-label={editing ? `Change ${medicine.name}` : `Add ${medicine.name}`}
      onSubmit={(event) => {
        void form.handleSubmit((values) => {
          save.mutate(values);
        })(event);
      }}
    >
      <p>
        <strong>{label(medicine)}</strong>{' '}
        {editing ? null : (
          <button
            type="button"
            className="secondary"
            onClick={() => {
              setMedicine(null);
            }}
          >
            Choose another
          </button>
        )}
      </p>

      <label htmlFor="rx-doseValue">Dose</label>
      <input inputMode="decimal" {...field('doseValue')} />
      {fieldError('doseValue')}

      <label htmlFor="rx-doseUnit">Unit</label>
      <select {...field('doseUnit')}>
        <option value="">Choose</option>
        {DOSE_UNITS.map((unit) => (
          <option key={unit} value={unit}>
            {unit.toLowerCase()}
          </option>
        ))}
      </select>

      <label htmlFor="rx-frequency">Frequency</label>
      <select {...field('frequency')}>
        <option value="">Choose</option>
        {Object.entries(FREQUENCIES).map(([value, text]) => (
          <option key={value} value={value}>
            {text}
          </option>
        ))}
      </select>

      <label htmlFor="rx-durationDays">Days</label>
      <input inputMode="numeric" {...field('durationDays')} />
      {fieldError('durationDays')}

      <label htmlFor="rx-route">Route</label>
      <select {...field('route')}>
        <option value="">Choose</option>
        {medicine.routes.map((route) => (
          <option key={route} value={route}>
            {route.toLowerCase()}
          </option>
        ))}
      </select>

      <label htmlFor="rx-quantity">Quantity to dispense</label>
      <input inputMode="numeric" {...field('quantity')} />
      {fieldError('quantity')}

      <label htmlFor="rx-instructions">Instructions (optional)</label>
      <textarea rows={2} {...field('instructions')} />
      {fieldError('instructions')}

      <div aria-live="polite">
        {save.error ? <p role="alert">{errorMessage(save.error)}</p> : null}
      </div>
      <div className="actions">
        <button type="submit" disabled={save.isPending}>
          {save.isPending ? 'Saving...' : editing ? 'Save changes' : 'Add to prescription'}
        </button>
        {onCancel ? (
          <button type="button" className="secondary" onClick={onCancel}>
            Cancel
          </button>
        ) : null}
      </div>
    </form>
  );
}
