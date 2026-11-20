import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { errorMessage } from '../../api/client';
import { duplicateDetails, useRegisterPatient } from '../patients/api';
import type { PatientSummaryResponse, RegisterPatientRequest } from '../patients/types';

const KENYAN_PHONE = /^(?:\+?254|0)?[17]\d{8}$/;

const optionalText = (max: number) => z.string().trim().max(max).optional();

const schema = z
  .object({
    fullName: z.string().trim().min(1, 'Enter the name').max(200),
    dateOfBirth: z
      .string()
      .min(1, 'Enter the date of birth')
      .refine(
        (value) => new Date(value) <= new Date(),
        'The date of birth cannot be in the future',
      ),
    sex: z.enum(['FEMALE', 'MALE', 'INTERSEX'], { message: 'Choose one' }),
    phone: optionalText(20).refine(
      (value) => !value || KENYAN_PHONE.test(value.replace(/[\s()-]/g, '')),
      'Use a Kenyan mobile number, for example 0712 345 678',
    ),
    nationalId: optionalText(9).refine(
      (value) => !value || /^\d{6,9}$/.test(value),
      'A national ID is 6 to 9 digits',
    ),
    payerType: z.enum(['CASH', 'SHA', 'INSURANCE']),
    shaNumber: optionalText(30),
    preferredLanguage: z.enum(['SWAHILI', 'ENGLISH']),
  })
  .refine((values) => values.payerType !== 'SHA' || Boolean(values.shaNumber), {
    path: ['shaNumber'],
    message: 'Enter the SHA number when SHA pays',
  });

type Form = z.infer<typeof schema>;

interface RegisterPatientFormProps {
  /** A patient to continue with: the new one, or an existing match reception picked. */
  onPatient: (patient: PatientSummaryResponse) => void;
}

function toRequest(values: Form, confirmNewPatient: boolean): RegisterPatientRequest {
  const blankToUndefined = (value: string | undefined) => (value ? value : undefined);
  return {
    fullName: values.fullName,
    dateOfBirth: values.dateOfBirth,
    sex: values.sex,
    phone: blankToUndefined(values.phone),
    nationalId: blankToUndefined(values.nationalId),
    payerType: values.payerType,
    shaNumber: blankToUndefined(values.shaNumber),
    preferredLanguage: values.preferredLanguage,
    confirmNewPatient,
  };
}

/**
 * Registering a patient. If the phone or national ID is already on file, the matches are
 * shown so reception picks the existing patient; a shared family phone can be confirmed as
 * a different person.
 */
export function RegisterPatientForm({ onPatient }: RegisterPatientFormProps) {
  const form = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: { payerType: 'CASH', preferredLanguage: 'SWAHILI' },
  });
  const register = useRegisterPatient();
  const duplicates = duplicateDetails(register.error);
  const { errors } = form.formState;

  const submit = (confirmNewPatient: boolean) =>
    form.handleSubmit((values) => {
      register.mutate(toRequest(values, confirmNewPatient), { onSuccess: onPatient });
    });

  const field = (name: keyof Form, label: string, input: React.ReactNode) => (
    <>
      <label htmlFor={`register-${name}`}>{label}</label>
      {input}
      {errors[name] ? (
        <p id={`register-${name}-error`} className="field-error">
          {errors[name].message}
        </p>
      ) : null}
    </>
  );
  const invalid = (name: keyof Form) => ({
    id: `register-${name}`,
    'aria-invalid': errors[name] ? true : undefined,
    'aria-describedby': errors[name] ? `register-${name}-error` : undefined,
  });

  return (
    <section aria-labelledby="register-heading">
      <h2 id="register-heading">Register a new patient</h2>
      <form
        noValidate
        onSubmit={(event) => {
          void submit(false)(event);
        }}
      >
        {field(
          'fullName',
          'Full name',
          <input {...invalid('fullName')} {...form.register('fullName')} />,
        )}
        {field(
          'dateOfBirth',
          'Date of birth',
          <input type="date" {...invalid('dateOfBirth')} {...form.register('dateOfBirth')} />,
        )}
        {field(
          'sex',
          'Sex',
          <select {...invalid('sex')} {...form.register('sex')} defaultValue="">
            <option value="" disabled>
              Choose
            </option>
            <option value="FEMALE">Female</option>
            <option value="MALE">Male</option>
            <option value="INTERSEX">Intersex</option>
          </select>,
        )}
        {field(
          'phone',
          'Phone (optional)',
          <input type="tel" inputMode="tel" {...invalid('phone')} {...form.register('phone')} />,
        )}
        {field(
          'nationalId',
          'National ID (optional)',
          <input inputMode="numeric" {...invalid('nationalId')} {...form.register('nationalId')} />,
        )}
        {field(
          'payerType',
          'Who pays',
          <select {...invalid('payerType')} {...form.register('payerType')}>
            <option value="CASH">Cash</option>
            <option value="SHA">SHA</option>
            <option value="INSURANCE">Insurance</option>
          </select>,
        )}
        {field(
          'shaNumber',
          'SHA number',
          <input {...invalid('shaNumber')} {...form.register('shaNumber')} />,
        )}
        {field(
          'preferredLanguage',
          'Preferred language',
          <select {...invalid('preferredLanguage')} {...form.register('preferredLanguage')}>
            <option value="SWAHILI">Swahili</option>
            <option value="ENGLISH">English</option>
          </select>,
        )}

        <div aria-live="polite">
          {register.error && !duplicates ? (
            <p role="alert">{errorMessage(register.error)}</p>
          ) : null}
        </div>
        <button type="submit" disabled={register.isPending}>
          {register.isPending ? 'Saving...' : 'Register'}
        </button>
      </form>

      {duplicates ? (
        <div role="alert" className="duplicates">
          <p>
            <strong>This patient may already be registered.</strong>{' '}
            {duplicates.confirmable
              ? 'Someone with the same phone number is on file.'
              : 'Someone with the same national ID is on file.'}
          </p>
          <ul>
            {duplicates.matches.map((match) => (
              <li key={match.id}>
                {match.fullName} ({match.patientNumber}, {match.ageYears} years){' '}
                <button
                  type="button"
                  onClick={() => {
                    onPatient(match);
                  }}
                >
                  Use this patient
                </button>
              </li>
            ))}
          </ul>
          {duplicates.confirmable ? (
            <button
              type="button"
              onClick={() => {
                void submit(true)();
              }}
            >
              Register as a different person
            </button>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}
