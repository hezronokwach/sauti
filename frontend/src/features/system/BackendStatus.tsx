import { ApiRequestError } from '../../api/client';
import { useBackendHealth } from './useBackendHealth';

/** Shows whether the backend answers through the proxy, refreshed every 30 seconds. */
export function BackendStatus() {
  const { data, error, isPending } = useBackendHealth();

  if (isPending) {
    return <p role="status">Checking the backend...</p>;
  }
  if (error) {
    const requestId = error instanceof ApiRequestError ? error.requestId : null;
    return (
      <p role="alert">
        Cannot reach the backend: {error.message}
        {requestId ? ` (request id ${requestId})` : ''}
      </p>
    );
  }
  return (
    <p role="status">
      Backend: <strong>{data.status}</strong>
    </p>
  );
}
