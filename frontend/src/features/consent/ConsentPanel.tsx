import { useState } from 'react';
import { errorMessage } from '../../api/client';
import type { PreferredLanguage } from '../patients/types';
import { isAgreed, useConsentHistory, useConsentWording, useRecordConsent } from './api';
import type { ConsentGivenBy, ConsentPurpose } from './api';

interface ConsentPanelProps {
  visitId: string;
  purpose: ConsentPurpose;
  /** The patient's preferred language; the wording is shown in it. */
  language: PreferredLanguage;
}

/**
 * Asking for consent to record. Shows the wording in the patient's language, records who
 * decided and what, and once agreed offers to record the patient's withdrawal. The
 * recorder (issue 25) only starts when {@link isAgreed} says so; the backend checks again.
 * After agreement the wording folds away to save room on the doctor's screen.
 */
export function ConsentPanel({ visitId, purpose, language }: ConsentPanelProps) {
  const wording = useConsentWording(language);
  const history = useConsentHistory(visitId);
  const record = useRecordConsent(visitId);
  const [givenBy, setGivenBy] = useState<ConsentGivenBy>('PATIENT');
  const agreed = isAgreed(history.data, purpose);

  const decide = (decision: 'AGREED' | 'REFUSED' | 'WITHDRAWN') => {
    record.mutate({ purpose, decision, givenBy });
  };

  return (
    <section aria-labelledby="consent-heading" className="panel">
      <h2 id="consent-heading">Consent to record</h2>
      {wording.data?.demonstration ? (
        <p className="notice">Demonstration wording, not approved for real patients.</p>
      ) : null}
      {wording.error ? <p role="alert">{errorMessage(wording.error)}</p> : null}
      {wording.data ? (
        agreed ? (
          // Once agreed the wording folds away, so the recorder and transcript stay in view.
          <details className="consent-details">
            <summary>Show the wording read to the patient</summary>
            <blockquote lang={language === 'SWAHILI' ? 'sw' : 'en'} className="consent-text">
              {wording.data.text}
            </blockquote>
          </details>
        ) : (
          <blockquote lang={language === 'SWAHILI' ? 'sw' : 'en'} className="consent-text">
            {wording.data.text}
          </blockquote>
        )
      ) : null}

      <div aria-live="polite">
        {agreed ? (
          <p role="status">
            <strong>Consent given.</strong> Recording is allowed.
          </p>
        ) : null}
        {record.error ? <p role="alert">{errorMessage(record.error)}</p> : null}
      </div>

      <label htmlFor="consent-given-by">Decided by</label>
      <select
        id="consent-given-by"
        value={givenBy}
        onChange={(event) => {
          setGivenBy(event.target.value as ConsentGivenBy);
        }}
      >
        <option value="PATIENT">The patient</option>
        <option value="PARENT_OR_GUARDIAN">Parent or guardian</option>
        <option value="REPRESENTATIVE">Representative</option>
      </select>

      {agreed ? (
        <button
          type="button"
          disabled={record.isPending}
          onClick={() => {
            decide('WITHDRAWN');
          }}
        >
          Patient withdraws consent
        </button>
      ) : (
        <div className="actions">
          <button
            type="button"
            disabled={record.isPending || !wording.data}
            onClick={() => {
              decide('AGREED');
            }}
          >
            Patient agrees
          </button>
          <button
            type="button"
            disabled={record.isPending || !wording.data}
            onClick={() => {
              decide('REFUSED');
            }}
          >
            Patient refuses
          </button>
        </div>
      )}
    </section>
  );
}
