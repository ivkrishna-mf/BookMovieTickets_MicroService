import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it } from 'vitest';
import { API } from '../test/fixtures';
import { server } from '../test/server';
import { ApiError, apiRequest, configureAuth, describeError } from './http';
import { bookingApi, catalogApi, toPage } from './index';

afterEach(() => configureAuth(async () => null, () => {}));

describe('apiRequest', () => {
  it('returns parsed JSON for a successful call', async () => {
    const page = await catalogApi.events('Mumbai');
    expect(page.items.length).toBeGreaterThan(0);
  });

  it('sends the city, page and size query parameters', async () => {
    let seen: URL | null = null;
    server.use(http.get(`${API}/api/v1/catalog/events`, ({ request }) => {
      seen = new URL(request.url);
      return HttpResponse.json({ items: [], page: 1, size: 10, totalElements: 0, totalPages: 0 });
    }));
    await catalogApi.events('New Delhi', 1, 10);
    expect(seen!.searchParams.get('city')).toBe('New Delhi');
    expect(seen!.searchParams.get('page')).toBe('1');
    expect(seen!.searchParams.get('size')).toBe('10');
  });

  it('maps RFC 7807 problem+json into ApiError with code and extra properties', async () => {
    server.use(http.post(`${API}/api/v1/bookings`, () =>
      HttpResponse.json({ status: 409, title: 'Conflict', code: 'SEATS_UNAVAILABLE', detail: 'Seats taken', unavailableSeats: ['A-1'] }, { status: 409 }),
    ));
    const error = await bookingApi.create('show-1', ['A-1'], 'key-1234567890abcdef').catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(409);
    expect(apiError.code).toBe('SEATS_UNAVAILABLE');
    expect(apiError.message).toBe('Seats taken');
    expect(apiError.problem.unavailableSeats).toEqual(['A-1']);
  });

  it('exposes Retry-After on 429 and explains it to the user', async () => {
    server.use(http.get(`${API}/api/v1/catalog/shows/:id`, () => new HttpResponse(null, { status: 429, headers: { 'Retry-After': '3' } })));
    const error = (await catalogApi.show('x').catch((e: unknown) => e)) as ApiError;
    expect(error.isRateLimited).toBe(true);
    expect(error.retryAfterSeconds).toBe(3);
    expect(describeError(error)).toContain('retry in 3s');
  });

  it('treats the gateway circuit-breaker fallback (503) as transient', async () => {
    server.use(http.get(`${API}/api/v1/shows/:id/seats`, () =>
      HttpResponse.json({ status: 503, code: 'DEPENDENCY_UNAVAILABLE', detail: 'seat-inventory is temporarily unavailable.' }, { status: 503 }),
    ));
    const error = (await import('./index').then((m) => m.inventoryApi.seatMap('s')).catch((e: unknown) => e)) as ApiError;
    expect(error.isTransient).toBe(true);
    expect(error.code).toBe('DEPENDENCY_UNAVAILABLE');
  });

  it('turns network failures into a NETWORK_ERROR ApiError', async () => {
    server.use(http.get(`${API}/api/v1/catalog/events/:id`, () => HttpResponse.error()));
    const error = (await catalogApi.event('x').catch((e: unknown) => e)) as ApiError;
    expect(error.status).toBe(0);
    expect(error.code).toBe('NETWORK_ERROR');
    expect(describeError(error)).toMatch(/Cannot reach BookMyTickets/);
  });

  it('attaches the bearer token and Idempotency-Key for authenticated writes', async () => {
    configureAuth(async () => 'token-abc', () => {});
    let headers: Headers | null = null;
    let body: unknown = null;
    server.use(http.post(`${API}/api/v1/bookings`, async ({ request }) => {
      headers = request.headers;
      body = await request.json();
      return HttpResponse.json({ id: 'b-1' }, { status: 201 });
    }));
    await bookingApi.create('show-1', ['A-1', 'A-2'], 'idem-key-1234567890');
    expect(headers!.get('authorization')).toBe('Bearer token-abc');
    expect(headers!.get('idempotency-key')).toBe('idem-key-1234567890');
    expect(body).toEqual({ showId: 'show-1', seats: ['A-1', 'A-2'] });
  });

  it('does not attach a token to public reads', async () => {
    configureAuth(async () => 'token-abc', () => {});
    let auth: string | null = 'unset';
    server.use(http.get(`${API}/api/v1/catalog/shows/:id`, ({ request }) => {
      auth = request.headers.get('authorization');
      return HttpResponse.json({});
    }));
    await catalogApi.show('s');
    expect(auth).toBeNull();
  });

  it('notifies the auth layer on 401 for authenticated calls', async () => {
    let unauthorized = 0;
    configureAuth(async () => 'expired', () => unauthorized++);
    server.use(http.get(`${API}/api/v1/bookings/:id`, () => new HttpResponse(null, { status: 401 })));
    await bookingApi.get('b').catch(() => undefined);
    expect(unauthorized).toBe(1);
  });

  it('normalises the VIA_DTO page shape returned by booking-service', async () => {
    server.use(http.get(`${API}/api/v1/bookings`, () =>
      HttpResponse.json({ content: [{ id: 'b' }], page: { size: 1, number: 1, totalElements: 3, totalPages: 3 } }),
    ));
    await expect(bookingApi.mine(1, 1)).resolves.toEqual({
      content: [{ id: 'b' }], number: 1, size: 1, totalElements: 3, totalPages: 3, first: false, last: false,
    });
  });

  it('also accepts the legacy flat Spring page shape', () => {
    expect(toPage({ content: [1, 2], number: 0, size: 2, totalElements: 2, totalPages: 1 })).toMatchObject({ first: true, last: true, totalPages: 1 });
    expect(toPage({ content: [] })).toMatchObject({ number: 0, totalPages: 1, first: true, last: true });
  });

  it('returns undefined for 202/204 responses', async () => {
    server.use(http.post(`${API}/api/v1/test`, () => new HttpResponse(null, { status: 202 })));
    await expect(apiRequest('/api/v1/test', { method: 'POST' })).resolves.toBeUndefined();
  });
});
