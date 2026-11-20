import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { bodyOf, jsonResponse, mockApi, renderWithQuery } from '../../test/render';
import type { ConsentResponse } from './api';
import { isAgreed } from './api';
import { ConsentPanel } from './ConsentPanel';

function decision(
  d: ConsentResponse['decision'],
  purpose: ConsentResponse['purpose'] = 'RECORD_CONSULTATION',
) {
  return {
    id: crypto.randomUUID(),
    visitId: 'visit-1',
    purpose,
    decision: d,
    givenBy: 'PATIENT',
    wordingVersion: 'v1-sw',
    recordedBy: 'staff-1',
    recordedAt: '2026-01-01T08:00:00Z',
  } satisfies ConsentResponse;
}

function backend(history: ConsentResponse[]) {
  return mockApi({
    'GET /api/consent/wording': () =>
      jsonResponse(200, {
        version: 'v1-sw',
        language: 'SWAHILI',
        text: 'MANENO YA MAJARIBIO. Je, unakubali?',
        demonstration: true,
      }),
    'GET /api/visits/visit-1/consents': () => jsonResponse(200, history),
    'POST /api/visits/visit-1/consents': (init) => {
      const body = bodyOf(init) as { decision: ConsentResponse['decision'] };
      history.push(decision(body.decision));
      return jsonResponse(201, history.at(-1));
    },
  });
}

describe('ConsentPanel', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=t; path=/';
  });

  it("shows the wording in the patient's language, marked as demonstration", async () => {
    backend([]);

    renderWithQuery(
      <ConsentPanel visitId="visit-1" purpose="RECORD_CONSULTATION" language="SWAHILI" />,
    );

    expect(await screen.findByText('MANENO YA MAJARIBIO. Je, unakubali?')).toBeInTheDocument();
    expect(
      screen.getByText('Demonstration wording, not approved for real patients.'),
    ).toBeInTheDocument();
  });

  it('records an agreement with who decided, then offers the withdrawal', async () => {
    const user = userEvent.setup();
    const fetchMock = backend([]);
    renderWithQuery(
      <ConsentPanel visitId="visit-1" purpose="RECORD_CONSULTATION" language="SWAHILI" />,
    );

    await screen.findByText('MANENO YA MAJARIBIO. Je, unakubali?');
    await user.selectOptions(screen.getByLabelText('Decided by'), 'PARENT_OR_GUARDIAN');
    await user.click(screen.getByRole('button', { name: 'Patient agrees' }));

    expect(await screen.findByText('Consent given.')).toBeInTheDocument();
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST');
    expect(bodyOf(post?.[1])).toEqual({
      purpose: 'RECORD_CONSULTATION',
      decision: 'AGREED',
      givenBy: 'PARENT_OR_GUARDIAN',
    });
    expect(screen.getByRole('button', { name: 'Patient withdraws consent' })).toBeInTheDocument();
  });

  it('records a withdrawal, after which recording is no longer allowed', async () => {
    const user = userEvent.setup();
    backend([decision('AGREED')]);
    renderWithQuery(
      <ConsentPanel visitId="visit-1" purpose="RECORD_CONSULTATION" language="SWAHILI" />,
    );

    await user.click(await screen.findByRole('button', { name: 'Patient withdraws consent' }));

    expect(await screen.findByRole('button', { name: 'Patient agrees' })).toBeInTheDocument();
    expect(screen.queryByText('Consent given.')).toBeNull();
  });

  it('counts only the latest decision for the same purpose', () => {
    expect(isAgreed([decision('AGREED')], 'RECORD_CONSULTATION')).toBe(true);
    expect(isAgreed([decision('AGREED'), decision('WITHDRAWN')], 'RECORD_CONSULTATION')).toBe(
      false,
    );
    expect(isAgreed([decision('AGREED', 'RECORD_RECEPTION')], 'RECORD_CONSULTATION')).toBe(false);
    expect(isAgreed(undefined, 'RECORD_CONSULTATION')).toBe(false);
  });
});
