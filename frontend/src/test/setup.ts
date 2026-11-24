import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// jsdom lays nothing out, so it has no scrollIntoView; pages call it to bring the next step
// into view.
Element.prototype.scrollIntoView = () => undefined;

afterEach(() => {
  cleanup();
});
