import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { apiPost, errorMessage } from '../../api/client';

const KENYAN_PHONE = /^(?:\+?254|0)?[17]\d{8}$/;

const schema = z.object({
  fullName: z.string().trim().min(1, 'Enter your name').max(200),
  dateOfBirth: z
    .string()
    .min(1, 'Enter your date of birth')
    .refine((value) => new Date(value) <= new Date(), 'The date of birth cannot be in the future'),
  sex: z.enum(['FEMALE', 'MALE', 'INTERSEX'], { message: 'Choose one' }),
  phone: z
    .string()
    .trim()
    .refine(
      (value) => KENYAN_PHONE.test(value.replace(/[\s()-]/g, '')),
      'Use a Kenyan mobile number, for example 0712 345 678',
    ),
  reasonForVisit: z.string().trim().max(500).optional(),
  preferredLanguage: z.enum(['SWAHILI', 'ENGLISH']),
});

type Form = z.infer<typeof schema>;

/**
 * The public pre-registration page, reached by QR code in the waiting area. It only saves:
 * the answer is a ticket code, never anything already on file.
 */
export function PreRegisterPage() {
  const form = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: { preferredLanguage: 'SWAHILI' },
  });
  const submit = useMutation({
    mutationFn: (values: Form) =>
      apiPost<{ ticketCode: string }>('/api/public/pre-registrations', {
        ...values,
        reasonForVisit: values.reasonForVisit || undefined,
      }),
  });
  const { errors } = form.formState;

  if (submit.data) {
    return (
      <main className="pre-register">
        <h1>Your ticket</h1>
        <p className="board-now">{submit.data.ticketCode}</p>
        <p>
          Show this code at the reception desk when it is called. / Onyesha nambari hii kwenye
          mapokezi itakapoitwa.
        </p>
      </main>
    );
  }

  const field = (name: keyof Form, label: string, input: React.ReactNode) => (
    <>
      <label htmlFor={`pre-${name}`}>{label}</label>
      {input}
      {errors[name] ? (
        <p id={`pre-${name}-error`} className="field-error">
          {errors[name].message}
        </p>
      ) : null}
    </>
  );
  const invalid = (name: keyof Form) => ({
    id: `pre-${name}`,
    'aria-invalid': errors[name] ? true : undefined,
    'aria-describedby': errors[name] ? `pre-${name}-error` : undefined,
  });

  return (
    <main className="pre-register">
      <h1>Register before you reach the desk</h1>
      <p className="notice">
        What you type here is kept only for today to save time at reception, and deleted at the end
        of the day. Reception will check it with you. Unachoandika hapa kinahifadhiwa leo tu na
        kufutwa mwisho wa siku.
      </p>
      <form
        noValidate
        onSubmit={(event) => {
          void form.handleSubmit((values) => {
            submit.mutate(values);
          })(event);
        }}
      >
        {field(
          'fullName',
          'Full name / Jina kamili',
          <input autoComplete="name" {...invalid('fullName')} {...form.register('fullName')} />,
        )}
        {field(
          'dateOfBirth',
          'Date of birth / Tarehe ya kuzaliwa',
          <input type="date" {...invalid('dateOfBirth')} {...form.register('dateOfBirth')} />,
        )}
        {field(
          'sex',
          'Sex / Jinsia',
          <select {...invalid('sex')} {...form.register('sex')} defaultValue="">
            <option value="" disabled>
              Choose / Chagua
            </option>
            <option value="FEMALE">Female / Mwanamke</option>
            <option value="MALE">Male / Mwanamume</option>
            <option value="INTERSEX">Intersex</option>
          </select>,
        )}
        {field(
          'phone',
          'Mobile number / Nambari ya simu',
          <input
            type="tel"
            inputMode="tel"
            autoComplete="tel"
            {...invalid('phone')}
            {...form.register('phone')}
          />,
        )}
        {field(
          'reasonForVisit',
          'Why are you here today? (optional) / Kwa nini umekuja leo?',
          <textarea
            maxLength={500}
            {...invalid('reasonForVisit')}
            {...form.register('reasonForVisit')}
          />,
        )}
        {field(
          'preferredLanguage',
          'Language / Lugha',
          <select {...invalid('preferredLanguage')} {...form.register('preferredLanguage')}>
            <option value="SWAHILI">Kiswahili</option>
            <option value="ENGLISH">English</option>
          </select>,
        )}
        <div aria-live="polite">
          {submit.error ? <p role="alert">{errorMessage(submit.error)}</p> : null}
        </div>
        <button type="submit" disabled={submit.isPending}>
          {submit.isPending ? 'Sending...' : 'Get my ticket / Pata tiketi'}
        </button>
      </form>
    </main>
  );
}
