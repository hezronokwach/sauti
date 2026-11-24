import { useNavigate } from 'react-router';
import { errorMessage } from '../../api/client';
import { ROLE_HOME } from '../auth/roles';
import { useFinishConsultation } from './api';

/**
 * Ends the consultation from the visit page and goes back to the doctor's queue. The server
 * refuses while the note is unsigned and says why; that message is shown here.
 */
export function FinishConsultation({ visitId }: { visitId: string }) {
  const navigate = useNavigate();
  const finish = useFinishConsultation();

  return (
    <div className="finish">
      <button
        type="button"
        disabled={finish.isPending}
        onClick={() => {
          // Awaited: finishing moves the visit on, which replaces this part of the page.
          void finish.mutateAsync(visitId).then(
            () => navigate(ROLE_HOME.DOCTOR),
            () => undefined,
          );
        }}
      >
        {finish.isPending ? 'Finishing...' : 'Finish consultation'}
      </button>
      {finish.error ? <p role="alert">{errorMessage(finish.error)}</p> : null}
    </div>
  );
}
