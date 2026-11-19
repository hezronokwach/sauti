import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse, renderRoute } from '../test/render';

describe('routes', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>().mockResolvedValue(jsonResponse(200, { status: 'UP' })),
    );
  });

  it('shows the home page at /', () => {
    renderRoute('/');

    expect(screen.getByRole('heading', { name: 'Sauti HMS' })).toBeInTheDocument();
  });

  it('shows the not-found page for an unknown path', () => {
    renderRoute('/no/such/page');

    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Go to the home page' })).toHaveAttribute('href', '/');
  });
});
