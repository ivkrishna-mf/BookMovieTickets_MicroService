import { apiRequest } from './http';
import type {
  BookingResponse, CreateEventRequest, CreateShowRequest, CreateVenueRequest, EventDetails, EventPage, IdResponse,
  Page, SeatMapResponse, ShowResponse, SpringPageDto,
} from './types';

const enc = encodeURIComponent;

/** Accepts both Spring page JSON shapes (VIA_DTO and the legacy flat PageImpl). */
export function toPage<T>(dto: SpringPageDto<T>): Page<T> {
  const meta = dto.page ?? dto;
  const content = dto.content ?? [];
  const number = meta.number ?? 0;
  const size = meta.size ?? content.length;
  const totalElements = meta.totalElements ?? content.length;
  const totalPages = meta.totalPages ?? 1;
  return { content, number, size, totalElements, totalPages, first: number <= 0, last: number >= totalPages - 1 };
}

/** catalog-service via gateway route "catalog" (public reads, ADMIN writes). */
export const catalogApi = {
  events: (city: string, page = 0, size = 100, signal?: AbortSignal) =>
    apiRequest<EventPage>(
      `/api/v1/catalog/events?${new URLSearchParams({ city, page: String(page), size: String(size) })}`,
      { signal },
    ),
  event: (id: string, signal?: AbortSignal) => apiRequest<EventDetails>(`/api/v1/catalog/events/${enc(id)}`, { signal }),
  show: (id: string, signal?: AbortSignal) => apiRequest<ShowResponse>(`/api/v1/catalog/shows/${enc(id)}`, { signal }),

  createVenue: (body: CreateVenueRequest) =>
    apiRequest<IdResponse>('/api/v1/catalog/venues', { method: 'POST', body, auth: true }),
  createEvent: (body: CreateEventRequest) =>
    apiRequest<IdResponse>('/api/v1/catalog/events', { method: 'POST', body, auth: true }),
  createShow: (body: CreateShowRequest) =>
    apiRequest<IdResponse>('/api/v1/catalog/shows', { method: 'POST', body, auth: true }),
  cancelShow: (id: string) =>
    apiRequest<void>(`/api/v1/catalog/shows/${enc(id)}/cancel`, { method: 'POST', auth: true }),
};

/** seat-inventory-service via gateway route "seat-map" (public). */
export const inventoryApi = {
  seatMap: (showId: string, signal?: AbortSignal) =>
    apiRequest<SeatMapResponse>(`/api/v1/shows/${enc(showId)}/seats`, { signal }),
};

/** booking-service via gateway route "bookings" (authenticated, strict per-user rate limit). */
export const bookingApi = {
  /**
   * Hold seats + create a payment. The Idempotency-Key MUST be reused on retries of the same intent
   * (timeouts, double clicks) so the booking executes at most once server-side.
   */
  create: (showId: string, seats: string[], idempotencyKey: string) =>
    apiRequest<BookingResponse>('/api/v1/bookings', {
      method: 'POST',
      auth: true,
      body: { showId, seats },
      headers: { 'Idempotency-Key': idempotencyKey },
    }),
  get: (id: string, signal?: AbortSignal) =>
    apiRequest<BookingResponse>(`/api/v1/bookings/${enc(id)}`, { auth: true, signal }),
  mine: async (page = 0, size = 20, signal?: AbortSignal) =>
    toPage(await apiRequest<SpringPageDto<BookingResponse>>(`/api/v1/bookings?page=${page}&size=${size}`, { auth: true, signal })),
  cancel: (id: string) =>
    apiRequest<BookingResponse>(`/api/v1/bookings/${enc(id)}/cancel`, { method: 'POST', auth: true }),
};

/** payment-service via gateway route "payments". */
export const paymentApi = {
  /** Local stack only (PAYMENT_PROVIDER=fake): simulates Stripe's payment_intent.succeeded webhook. */
  simulateSuccess: (bookingId: string) =>
    apiRequest<void>(`/api/v1/payments/dev/bookings/${enc(bookingId)}/succeed`, { method: 'POST', auth: true }),
};

export const queryKeys = {
  events: (city: string) => ['events', city] as const,
  event: (id: string) => ['event', id] as const,
  show: (id: string) => ['show', id] as const,
  seatMap: (id: string) => ['seatMap', id] as const,
  booking: (id: string) => ['booking', id] as const,
  myBookings: (page: number) => ['myBookings', page] as const,
};
