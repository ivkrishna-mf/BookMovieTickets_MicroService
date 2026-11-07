import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { buildShowPayload } from './pages/AdminPage';
import { matchesFilter } from './pages/MyBookingsPage';
import { groupShowsByDay } from './pages/EventDetailsPage';
import { API, EVENT_DETAILS, booking, signIn } from './test/fixtures';
import { renderApp } from './test/render';
import { server } from './test/server';

describe('navigation & layout', () => {
  it('shows the primary tabs and hides Admin for regular users', async () => {
    signIn(['USER']);
    renderApp('/');
    const nav = screen.getByRole('navigation', { name: 'Primary' });
    for (const label of ['Home', 'Movies', 'Events', 'My Bookings']) {
      expect(within(nav).getByRole('link', { name: label })).toBeInTheDocument();
    }
    expect(within(nav).queryByRole('link', { name: 'Admin' })).not.toBeInTheDocument();
    expect(screen.getByText('Alice')).toBeInTheDocument();
  });

  it('shows the Admin tab for admins', () => {
    signIn(['USER', 'ADMIN'], 'admin');
    renderApp('/');
    expect(within(screen.getByRole('navigation', { name: 'Primary' })).getByRole('link', { name: 'Admin' })).toBeInTheDocument();
  });

  it('offers sign-in when anonymous', () => {
    renderApp('/');
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });

  it('renders a 404 page for unknown routes', () => {
    renderApp('/nope');
    expect(screen.getByText('404')).toBeInTheDocument();
  });
});

describe('home', () => {
  it('loads events for the selected city from catalog-service', async () => {
    renderApp('/');
    expect(await screen.findByRole('heading', { level: 1, name: 'Interstellar' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Now showing in Mumbai' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Live events near you' })).toBeInTheDocument();
  });

  it('refetches when the city changes and shows an empty state', async () => {
    const { user } = renderApp('/');
    await screen.findByRole('heading', { level: 1, name: 'Interstellar' });
    await user.selectOptions(screen.getByLabelText('Select city'), 'Delhi');
    expect(await screen.findByText(/Nothing playing in Delhi yet/)).toBeInTheDocument();
  });

  it('shows a retryable error when the gateway is down', async () => {
    server.use(http.get(`${API}/api/v1/catalog/events`, () =>
      HttpResponse.json({ status: 503, code: 'DEPENDENCY_UNAVAILABLE', detail: 'catalog is temporarily unavailable. Please retry shortly.' }, { status: 503 }),
    ));
    renderApp('/');
    expect(await screen.findByText(/catalog is temporarily unavailable/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument();
  });
});

describe('browse', () => {
  it('lists only movies on the Movies tab and filters by search', async () => {
    const { user } = renderApp('/movies');
    expect(await screen.findByRole('link', { name: /Interstellar, Movie/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Pathaan, Movie/ })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Arijit Live/ })).not.toBeInTheDocument();

    await user.type(screen.getByRole('searchbox', { name: 'Search movies' }), 'path');
    await waitFor(() => expect(screen.queryByRole('link', { name: /Interstellar, Movie/ })).not.toBeInTheDocument());
    expect(screen.getByRole('link', { name: /Pathaan, Movie/ })).toBeInTheDocument();
  });

  it('filters events by category tab', async () => {
    const { user } = renderApp('/events');
    expect(await screen.findByRole('link', { name: /Arijit Live/ })).toBeInTheDocument();
    await user.click(screen.getByRole('tab', { name: 'Sports' }));
    await waitFor(() => expect(screen.queryByRole('link', { name: /Arijit Live/ })).not.toBeInTheDocument());
    expect(screen.getByRole('link', { name: /IPL Final/ })).toBeInTheDocument();
  });

  it('filters by language chip', async () => {
    const { user } = renderApp('/movies');
    await screen.findByRole('link', { name: /Interstellar, Movie/ });
    await user.click(screen.getByRole('button', { name: 'Hindi' }));
    await waitFor(() => expect(screen.queryByRole('link', { name: /Interstellar, Movie/ })).not.toBeInTheDocument());
  });
});

describe('event details', () => {
  it('shows showtimes in the selected city grouped by venue, hiding cancelled shows', async () => {
    const { user } = renderApp(`/events/${EVENT_DETAILS.id}`);
    expect(await screen.findByRole('heading', { level: 1, name: 'Interstellar' })).toBeInTheDocument();
    expect(screen.getByText(/wormhole/)).toBeInTheDocument();

    const dates = screen.getAllByRole('tab');
    expect(dates).toHaveLength(2);
    expect(screen.getByText('PVR Phoenix')).toBeInTheDocument();
    expect(screen.queryByText('PVR Select')).not.toBeInTheDocument(); // Delhi

    await user.click(dates[1]!);
    expect(await screen.findByText('INOX Nariman')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: /^Book .* show at/ })).toHaveLength(1); // cancelled show-4 hidden

    await user.click(screen.getByRole('checkbox'));
    await user.click(screen.getAllByRole('tab')[0]!);
    expect(await screen.findByText('PVR Select')).toBeInTheDocument();
  });

  it('shows the API error for a missing event', async () => {
    renderApp('/events/unknown');
    expect(await screen.findByText('Event not found')).toBeInTheDocument();
  });

  it('groups shows by day and venue', () => {
    const days = groupShowsByDay(EVENT_DETAILS.upcomingShows);
    expect(days.size).toBe(2);
    const total = [...days.values()].flat().reduce((n, v) => n + v.shows.length, 0);
    expect(total).toBe(3);
  });
});

describe('seat selection & booking', () => {
  it('selects seats, prices them and enforces the max seats per booking', async () => {
    const { user } = renderApp('/shows/show-1');
    await user.click(await screen.findByRole('button', { name: 'Seat A-1, Gold, available' }));
    await user.click(screen.getByRole('button', { name: 'Seat C-2, Recliner, available' }));
    const summary = screen.getByRole('complementary', { name: 'Booking summary' });
    expect(within(summary).getByText(/₹\s?1,100/)).toBeInTheDocument();
    expect(within(summary).getByText('2 / 3 seats')).toBeInTheDocument();

    expect(screen.getByRole('button', { name: 'Seat A-3, Gold, unavailable' })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Seat A-2, Gold, available' }));
    await user.click(screen.getByRole('button', { name: 'Seat A-4, Gold, available' }));
    expect(await screen.findByText('You can book up to 3 seats per booking.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Seat A-4, Gold, available' })).toHaveAttribute('aria-pressed', 'false');

    // anonymous users are asked to sign in before booking
    expect(within(summary).getByRole('button', { name: /sign in to book/i })).toBeInTheDocument();
  });

  it('creates a booking with an Idempotency-Key and moves to checkout', async () => {
    const token = signIn();
    let request: { auth: string | null; key: string | null; body: unknown } | null = null;
    server.use(http.post(`${API}/api/v1/bookings`, async ({ request: r }) => {
      request = { auth: r.headers.get('authorization'), key: r.headers.get('idempotency-key'), body: await r.json() };
      return HttpResponse.json(booking({ id: 'b-new' }), { status: 201 });
    }));
    const { user } = renderApp('/shows/show-1');
    await user.click(await screen.findByRole('button', { name: 'Seat A-2, Gold, available' }));
    await user.click(screen.getByRole('button', { name: 'Seat A-1, Gold, available' }));
    await user.click(screen.getByRole('button', { name: /book now/i }));

    expect(await screen.findByRole('heading', { name: 'Checkout' })).toBeInTheDocument();
    expect(request!.auth).toBe(`Bearer ${token}`);
    expect(request!.key).toMatch(/^[A-Za-z0-9-]{16,64}$/);
    expect(request!.body).toEqual({ showId: 'show-1', seats: ['A-1', 'A-2'] });
  });

  it('handles a 409 seat conflict by releasing the taken seats', async () => {
    signIn();
    server.use(http.post(`${API}/api/v1/bookings`, () =>
      HttpResponse.json({ status: 409, code: 'SEATS_UNAVAILABLE', detail: 'Some seats are no longer available', unavailableSeats: ['B-1'] }, { status: 409 }),
    ));
    const { user } = renderApp('/shows/show-1');
    await user.click(await screen.findByRole('button', { name: 'Seat B-1, Gold, available' }));
    await user.click(screen.getByRole('button', { name: 'Seat B-2, Gold, available' }));
    await user.click(screen.getByRole('button', { name: /book now/i }));

    expect(await screen.findByText('Some seats are no longer available')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Seat B-1, Gold, available' })).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByRole('button', { name: 'Seat B-2, Gold, selected' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('takes the user to their in-progress booking on ACTIVE_BOOKING_EXISTS', async () => {
    signIn();
    server.use(
      // exact problem+json observed from booking-service on the running stack
      http.post(`${API}/api/v1/bookings`, () =>
        HttpResponse.json({ type: 'about:blank', title: 'Conflict', status: 409, detail: 'You already have a booking in progress for this show', code: 'ACTIVE_BOOKING_EXISTS' }, { status: 409 }),
      ),
      http.get(`${API}/api/v1/bookings`, () =>
        HttpResponse.json({ content: [booking({ id: 'b-active', status: 'PENDING_PAYMENT' })], page: { size: 50, number: 0, totalElements: 1, totalPages: 1 } }),
      ),
    );
    const { user } = renderApp('/shows/show-1');
    await user.click(await screen.findByRole('button', { name: 'Seat B-3, Gold, available' }));
    await user.click(screen.getByRole('button', { name: /book now/i }));
    expect(await screen.findByText(/already have a booking in progress.*taking you there/)).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Checkout' })).toBeInTheDocument();
    expect(screen.getByText('Booking #B-ACTIVE')).toBeInTheDocument();
  });

  it('tells the user when they are rate limited', async () => {
    signIn();
    server.use(http.post(`${API}/api/v1/bookings`, () => new HttpResponse(null, { status: 429, headers: { 'Retry-After': '2' } })));
    const { user } = renderApp('/shows/show-1');
    await user.click(await screen.findByRole('button', { name: 'Seat A-1, Gold, available' }));
    await user.click(screen.getByRole('button', { name: /book now/i }));
    expect(await screen.findByText(/retry in 2s/)).toBeInTheDocument();
  });
});

describe('checkout', () => {
  it('requires sign-in', () => {
    renderApp('/bookings/b-1');
    expect(screen.getByRole('heading', { name: 'Sign in to continue' })).toBeInTheDocument();
  });

  it('pays (fake provider), polls the saga and reveals the e-ticket', async () => {
    signIn();
    let paid = false;
    server.use(
      http.post(`${API}/api/v1/payments/dev/bookings/:id/succeed`, () => {
        paid = true;
        return new HttpResponse(null, { status: 202 });
      }),
      http.get(`${API}/api/v1/bookings/:id`, () =>
        HttpResponse.json(paid ? booking({ status: 'CONFIRMED', ticketCode: 'TKT-XYZ-1' }) : booking()),
      ),
    );
    const { user } = renderApp('/bookings/b-1111-2222');
    expect(await screen.findByRole('timer')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /pay ₹\s?700/i }));

    expect(await screen.findByRole('heading', { name: "You're all set!" }, { timeout: 4000 })).toBeInTheDocument();
    expect(screen.getByText('Confirmed')).toBeInTheDocument();
    expect(await screen.findByText('TKT-XYZ-1')).toBeInTheDocument(); // mounts after the pending panel's exit
    expect(await screen.findByAltText(/Entry QR code/)).toBeInTheDocument();
  });

  it('cancels a pending booking', async () => {
    signIn();
    server.use(http.post(`${API}/api/v1/bookings/:id/cancel`, () => HttpResponse.json(booking({ status: 'CANCELLED' }))));
    const { user } = renderApp('/bookings/b-1111-2222');
    await user.click(await screen.findByRole('button', { name: /cancel & release seats/i }));
    expect(await screen.findByRole('heading', { name: 'Booking cancelled' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Pick seats again' })).toHaveAttribute('href', '/shows/show-1');
  });

  it('explains an expired hold', async () => {
    signIn();
    server.use(http.get(`${API}/api/v1/bookings/:id`, () => HttpResponse.json(booking({ status: 'EXPIRED', expiresAt: null }))));
    renderApp('/bookings/b-1');
    expect(await screen.findByRole('heading', { name: 'Your seat hold expired' })).toBeInTheDocument();
  });
});

describe('my bookings', () => {
  it('lists the user bookings and filters them', async () => {
    signIn();
    const { user } = renderApp('/bookings');
    expect(await screen.findByText('Interstellar')).toBeInTheDocument();
    expect(screen.getByText('Pathaan')).toBeInTheDocument();
    await user.click(screen.getByRole('tab', { name: 'Upcoming' }));
    await waitFor(() => expect(screen.queryByText('Pathaan')).not.toBeInTheDocument());
    expect(screen.getByText('Interstellar')).toBeInTheDocument();
  });

  it('classifies bookings', () => {
    const now = Date.now();
    expect(matchesFilter(booking({ status: 'CONFIRMED' }), 'upcoming', now)).toBe(true);
    expect(matchesFilter(booking({ status: 'PENDING_PAYMENT' }), 'pending', now)).toBe(true);
    expect(matchesFilter(booking({ status: 'CONFIRMED', showTime: new Date(now - 1000).toISOString() }), 'past', now)).toBe(true);
  });
});

describe('admin', () => {
  it('is restricted to admins', () => {
    signIn(['USER']);
    renderApp('/admin');
    expect(screen.getByRole('heading', { name: 'Restricted area' })).toBeInTheDocument();
  });

  it('creates a venue through catalog-service', async () => {
    signIn(['USER', 'ADMIN'], 'admin');
    let body: unknown = null;
    server.use(http.post(`${API}/api/v1/catalog/venues`, async ({ request }) => {
      body = await request.json();
      return HttpResponse.json({ id: 'venue-123' }, { status: 201 });
    }));
    const { user } = renderApp('/admin');
    await user.type(await screen.findByLabelText('Name'), 'PVR Juhu');
    await user.click(screen.getByRole('button', { name: 'Create venue' }));
    expect(await screen.findByText('venue-123')).toBeInTheDocument();
    expect(body).toEqual({ name: 'PVR Juhu', city: 'Mumbai', address: '', capacity: 120 });
  });

  it('builds the CreateShowRequest seat layout and price map', () => {
    const payload = buildShowPayload(
      { eventId: 'e', venueId: 'v', startTime: '2030-01-01T10:00:00.000Z', currency: 'INR', maxSeatsPerBooking: 6 },
      [{ category: ' gold ', rows: 'a, b  c', seatsPerRow: 12, price: 350 }],
    );
    expect(payload.sections).toEqual([{ category: 'GOLD', rows: ['A', 'B', 'C'], seatsPerRow: 12 }]);
    expect(payload.priceByCategory).toEqual({ GOLD: 350 });
  });
});
