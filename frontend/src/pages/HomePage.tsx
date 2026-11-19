import { BackendStatus } from '../features/system/BackendStatus';

export function HomePage() {
  return (
    <main>
      <h1>Sauti HMS</h1>
      <p>Demonstration system. Do not enter real patient information.</p>
      <BackendStatus />
    </main>
  );
}
