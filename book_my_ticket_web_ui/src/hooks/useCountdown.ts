import { useEffect, useState } from 'react';

/** Milliseconds remaining until `deadline` (ISO string), ticking every second. null when there is no deadline. */
export function useCountdown(deadline: string | null | undefined): number | null {
  const target = deadline ? new Date(deadline).getTime() : null;
  const [remaining, setRemaining] = useState<number | null>(() => (target ? Math.max(0, target - Date.now()) : null));

  useEffect(() => {
    if (!target) {
      setRemaining(null);
      return;
    }
    const tick = () => setRemaining(Math.max(0, target - Date.now()));
    tick();
    const id = window.setInterval(tick, 1000);
    return () => window.clearInterval(id);
  }, [target]);

  return remaining;
}
