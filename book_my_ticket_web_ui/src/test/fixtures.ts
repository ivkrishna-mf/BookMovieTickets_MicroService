import type { BookingResponse, EventDetails, EventSummary, SeatMapResponse, SeatView, ShowResponse } from '../api/types';

export const API = 'http://localhost:3000';

export const EVENTS: EventSummary[] = [
  { id: 'e-movie-1', title: 'Interstellar', category: 'MOVIE', language: 'English', durationMinutes: 169 },
  { id: 'e-movie-2', title: 'Pathaan', category: 'MOVIE', language: 'Hindi', durationMinutes: 146 },
  { id: 'e-concert-1', title: 'Arijit Live', category: 'CONCERT', language: 'Hindi', durationMinutes: 180 },
  { id: 'e-sport-1', title: 'IPL Final', category: 'SPORTS', language: null, durationMinutes: 240 },
  { id: 'e-comedy-1', title: 'Late Night Laughs', category: 'COMEDY', language: 'English', durationMinutes: 90 },
];

const inTwoDays = new Date(Date.now() + 2 * 86_400_000);
inTwoDays.setHours(18, 30, 0, 0);
const inThreeDays = new Date(Date.now() + 3 * 86_400_000);
inThreeDays.setHours(21, 0, 0, 0);

export const EVENT_DETAILS: EventDetails = {
  ...EVENTS[0]!,
  description: 'A team of explorers travel through a wormhole in space.',
  upcomingShows: [
    { id: 'show-1', venueId: 'v-1', venueName: 'PVR Phoenix', city: 'Mumbai', startTime: inTwoDays.toISOString(), status: 'SCHEDULED' },
    { id: 'show-2', venueId: 'v-2', venueName: 'INOX Nariman', city: 'Mumbai', startTime: inThreeDays.toISOString(), status: 'SCHEDULED' },
    { id: 'show-3', venueId: 'v-3', venueName: 'PVR Select', city: 'Delhi', startTime: inTwoDays.toISOString(), status: 'SCHEDULED' },
    { id: 'show-4', venueId: 'v-1', venueName: 'PVR Phoenix', city: 'Mumbai', startTime: inThreeDays.toISOString(), status: 'CANCELLED' },
  ],
};

export const SHOW: ShowResponse = {
  id: 'show-1',
  eventId: 'e-movie-1',
  eventTitle: 'Interstellar',
  venueId: 'v-1',
  venueName: 'PVR Phoenix',
  city: 'Mumbai',
  startTime: inTwoDays.toISOString(),
  status: 'SCHEDULED',
  currency: 'INR',
  maxSeatsPerBooking: 3,
  priceByCategory: { GOLD: 350, RECLINER: 750 },
};

function row(rowLabel: string, category: string, count: number, taken: number[] = []): SeatView[] {
  return Array.from({ length: count }, (_, i) => ({
    seatNumber: `${rowLabel}-${i + 1}`,
    row: rowLabel,
    index: i + 1,
    category,
    status: taken.includes(i + 1) ? 'BOOKED' : 'AVAILABLE',
  }));
}

export const SEAT_MAP: SeatMapResponse = {
  showId: 'show-1',
  available: 0,
  seats: [...row('A', 'GOLD', 6, [3]), ...row('B', 'GOLD', 6), ...row('C', 'RECLINER', 4, [1])],
};
SEAT_MAP.available = SEAT_MAP.seats.filter((s) => s.status === 'AVAILABLE').length;

export function booking(overrides: Partial<BookingResponse> = {}): BookingResponse {
  return {
    id: 'b-1111-2222',
    status: 'PENDING_PAYMENT',
    showId: 'show-1',
    eventTitle: 'Interstellar',
    venueName: 'PVR Phoenix',
    showTime: inTwoDays.toISOString(),
    seats: ['A-1', 'A-2'],
    totalAmount: 700,
    currency: 'INR',
    paymentClientSecret: 'pi_secret',
    expiresAt: new Date(Date.now() + 8 * 60_000).toISOString(),
    ticketCode: null,
    failureReason: null,
    createdAt: new Date(Date.now() - 2 * 60_000).toISOString(),
    ...overrides,
  };
}

/** Unsigned JWT good enough for client-side decoding (the gateway validates real ones). */
export function fakeJwt(claims: Record<string, unknown>): string {
  const b64 = (o: unknown) => btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${b64({ alg: 'none', typ: 'JWT' })}.${b64(claims)}.sig`;
}

export function signIn(roles: string[] = ['USER'], username = 'alice'): string {
  const accessToken = fakeJwt({ sub: `${username}-id`, preferred_username: username, name: username === 'alice' ? 'Alice' : 'Admin', realm_access: { roles } });
  sessionStorage.setItem(
    'bmt.tokens',
    JSON.stringify({ accessToken, refreshToken: 'refresh', idToken: 'id', expiresAt: Date.now() + 600_000 }),
  );
  return accessToken;
}
