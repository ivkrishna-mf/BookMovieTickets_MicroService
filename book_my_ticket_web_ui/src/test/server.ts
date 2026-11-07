import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { API, EVENT_DETAILS, EVENTS, SEAT_MAP, SHOW, booking } from './fixtures';

/** Default happy-path handlers mirroring the API gateway routes. Tests override per case with server.use(). */
export const handlers = [
  http.get(`${API}/api/v1/catalog/events`, ({ request }) => {
    const city = new URL(request.url).searchParams.get('city');
    const items = city === 'Mumbai' ? EVENTS : [];
    return HttpResponse.json({ items, page: 0, size: 100, totalElements: items.length, totalPages: 1 });
  }),
  http.get(`${API}/api/v1/catalog/events/:id`, ({ params }) =>
    params.id === EVENT_DETAILS.id
      ? HttpResponse.json(EVENT_DETAILS)
      : HttpResponse.json({ status: 404, code: 'NOT_FOUND', detail: 'Event not found' }, { status: 404 }),
  ),
  http.get(`${API}/api/v1/catalog/shows/:id`, () => HttpResponse.json(SHOW)),
  http.get(`${API}/api/v1/shows/:id/seats`, () => HttpResponse.json(SEAT_MAP)),
  // booking-service serialises pages VIA_DTO (verified against the running stack).
  http.get(`${API}/api/v1/bookings`, () =>
    HttpResponse.json({
      content: [booking({ status: 'CONFIRMED', ticketCode: 'TKT-123' }), booking({ id: 'b-2', status: 'CANCELLED', eventTitle: 'Pathaan' })],
      page: { size: 12, number: 0, totalElements: 2, totalPages: 1 },
    }),
  ),
  http.get(`${API}/api/v1/bookings/:id`, ({ params }) => HttpResponse.json(booking({ id: String(params.id) }))),
];

export const server = setupServer(...handlers);
