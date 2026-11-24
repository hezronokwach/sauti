import { useQueueBoard } from './api';

/**
 * The waiting-area screen: no login, ticket codes only, refreshed every 5 seconds. The
 * newest called code is shown large; emergencies are never shown.
 */
export function WaitingAreaPage() {
  const board = useQueueBoard();
  const [now, ...before] = board.data?.called ?? [];

  return (
    <main className="waiting-area">
      <h1>Sauti Clinic</h1>
      {board.error ? (
        <p role="alert">The display is reconnecting...</p>
      ) : (
        <div className="columns">
          <section aria-labelledby="calling-heading">
            <h2 id="calling-heading">Now calling</h2>
            <p className="board-now" aria-live="assertive">
              {now ?? '-'}
            </p>
            {before.length > 0 ? <p className="board-before">{before.join('   ')}</p> : null}
          </section>
          <section aria-labelledby="waiting-heading">
            <h2 id="waiting-heading">Waiting</h2>
            {board.data && board.data.waiting.length === 0 ? <p>Nobody waiting.</p> : null}
            <ul className="board-waiting">
              {(board.data?.waiting ?? []).map((code) => (
                <li key={code}>{code}</li>
              ))}
            </ul>
          </section>
        </div>
      )}
    </main>
  );
}
