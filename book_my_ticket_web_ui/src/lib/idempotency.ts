/**
 * One Idempotency-Key per booking *intent* (show + exact seat set). Retrying the same intent
 * (network timeout, double click, page refresh mid-request) reuses the key, so booking-service
 * replays the original result instead of creating a second booking. Changing the seats is a new intent.
 */
const PREFIX = 'bmt.idem.';

export function intentFingerprint(showId: string, seats: string[]): string {
  return `${showId}|${[...new Set(seats)].sort().join(',')}`;
}

export function newKey(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID();
  // Fallback for non-secure contexts (plain http on a LAN host) where randomUUID is unavailable.
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}

export function idempotencyKeyFor(showId: string, seats: string[]): string {
  const storageKey = PREFIX + intentFingerprint(showId, seats);
  try {
    const existing = sessionStorage.getItem(storageKey);
    if (existing) return existing;
    const key = newKey();
    sessionStorage.setItem(storageKey, key);
    return key;
  } catch {
    return newKey();
  }
}

/** Forget the key once the intent reached a terminal outcome, so a later identical selection is a fresh booking. */
export function clearIdempotencyKey(showId: string, seats: string[]): void {
  try {
    sessionStorage.removeItem(PREFIX + intentFingerprint(showId, seats));
  } catch {
    /* ignore */
  }
}
