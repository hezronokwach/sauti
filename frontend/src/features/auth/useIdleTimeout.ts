import { useEffect, useRef } from 'react';

/** Ten minutes without input locks the screen; the server's 15-minute timeout backs it up. */
export const IDLE_TIMEOUT_MS = 10 * 60 * 1000;

const ACTIVITY_EVENT = 'sauti:activity';

const INPUT_EVENTS = ['keydown', 'pointerdown', 'pointermove', 'wheel', 'touchstart', 'scroll'];

/**
 * Counts as activity without any input, for work that has no clicks or key presses
 * (recording a consultation). Call it regularly while that work goes on.
 */
export function reportActivity(): void {
  window.dispatchEvent(new Event(ACTIVITY_EVENT));
}

/**
 * Calls {@code onIdle} once nothing has happened for {@code timeoutMs}. A sleeping tab
 * delays timers, so the time since the last activity is also checked when the tab
 * becomes visible again.
 */
export function useIdleTimeout(timeoutMs: number, onIdle: () => void): void {
  const onIdleRef = useRef(onIdle);
  useEffect(() => {
    onIdleRef.current = onIdle;
  });

  useEffect(() => {
    let lastActivity = Date.now();
    let fired = false;
    const fire = () => {
      if (!fired) {
        fired = true;
        onIdleRef.current();
      }
    };
    let timer = window.setTimeout(fire, timeoutMs);
    const onActivity = () => {
      lastActivity = Date.now();
      window.clearTimeout(timer);
      timer = window.setTimeout(fire, timeoutMs);
    };
    const onVisible = () => {
      if (document.visibilityState === 'visible' && Date.now() - lastActivity >= timeoutMs) {
        fire();
      }
    };
    const events = [...INPUT_EVENTS, ACTIVITY_EVENT];
    events.forEach((name) => {
      window.addEventListener(name, onActivity, { passive: true });
    });
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      window.clearTimeout(timer);
      events.forEach((name) => {
        window.removeEventListener(name, onActivity);
      });
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [timeoutMs]);
}
