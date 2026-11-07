import { describe, expect, it } from 'vitest';
import { EVENTS, SEAT_MAP } from '../test/fixtures';
import { EVENT_TABS, filterByTab, isMovie, languagesOf, searchEvents } from './categories';
import { dayKey, dayLabel, formatCountdown, formatDuration, formatMoney, titleCase } from './format';
import { clearIdempotencyKey, idempotencyKeyFor, intentFingerprint } from './idempotency';
import { hashString, initialsOf, posterArt } from './poster';
import { groupSeats, reconcileSelection, sortSeatLabels, toggleSeat, totalPrice } from './seats';

const seat = (n: string) => SEAT_MAP.seats.find((s) => s.seatNumber === n)!;

describe('format', () => {
  it('formats money in the booking currency', () => {
    expect(formatMoney(700, 'INR')).toMatch(/₹\s?700/);
    expect(formatMoney(12.5, 'USD', 'en-US')).toBe('$12.50');
  });

  it('falls back gracefully for an invalid currency code', () => {
    expect(formatMoney(5, 'NOPE!')).toBe('NOPE! 5.00');
  });

  it('formats durations', () => {
    expect(formatDuration(169)).toBe('2h 49m');
    expect(formatDuration(120)).toBe('2h');
    expect(formatDuration(45)).toBe('45m');
    expect(formatDuration(0)).toBe('');
  });

  it('formats countdowns and never goes negative', () => {
    expect(formatCountdown(125_000)).toBe('2:05');
    expect(formatCountdown(-10)).toBe('0:00');
  });

  it('title-cases enum values', () => {
    expect(titleCase('PENDING_PAYMENT')).toBe('Pending Payment');
    expect(titleCase('MOVIE')).toBe('Movie');
  });

  it('builds local day keys and relative labels', () => {
    const now = new Date(2026, 10, 7, 10, 0);
    expect(dayKey(new Date(2026, 10, 7, 23, 59).toISOString())).toBe('2026-11-07');
    expect(dayLabel('2026-11-07', now).weekday).toBe('Today');
    expect(dayLabel('2026-11-08', now).weekday).toBe('Tomorrow');
    expect(dayLabel('2026-11-10', now).day).toBe('10');
  });
});

describe('seats', () => {
  it('groups seats into category sections and ordered rows', () => {
    const sections = groupSeats(SEAT_MAP.seats);
    expect(sections.map((s) => s.category)).toEqual(['GOLD', 'RECLINER']);
    expect(sections[0]!.rows.map((r) => r.row)).toEqual(['A', 'B']);
    expect(sections[0]!.rows[0]!.seats.map((s) => s.index)).toEqual([1, 2, 3, 4, 5, 6]);
  });

  it('orders multi-letter rows after single-letter rows (Z before AA)', () => {
    const rows = groupSeats([
      { seatNumber: 'AA-1', row: 'AA', index: 1, category: 'X', status: 'AVAILABLE' },
      { seatNumber: 'Z-1', row: 'Z', index: 1, category: 'X', status: 'AVAILABLE' },
    ])[0]!.rows.map((r) => r.row);
    expect(rows).toEqual(['Z', 'AA']);
  });

  it('toggles available seats, rejects taken seats and enforces the per-booking limit', () => {
    expect(toggleSeat([], seat('A-1'), 2)).toEqual({ selected: ['A-1'] });
    expect(toggleSeat(['A-1'], seat('A-1'), 2)).toEqual({ selected: [] });
    expect(toggleSeat([], seat('A-3'), 2)).toEqual({ selected: [], rejected: 'unavailable' });
    expect(toggleSeat(['A-1', 'A-2'], seat('B-1'), 2)).toEqual({ selected: ['A-1', 'A-2'], rejected: 'limit' });
  });

  it('prices the selection by category', () => {
    expect(totalPrice(['A-1', 'C-2'], SEAT_MAP.seats, { GOLD: 350, RECLINER: 750 })).toBe(1100);
    expect(totalPrice(['ZZ-9'], SEAT_MAP.seats, { GOLD: 350 })).toBe(0);
  });

  it('drops selected seats that another user took', () => {
    expect(reconcileSelection(['A-1', 'A-3'], SEAT_MAP.seats)).toEqual({ kept: ['A-1'], lost: ['A-3'] });
  });

  it('sorts seat labels naturally', () => {
    expect(sortSeatLabels(['A-10', 'B-1', 'A-2'])).toEqual(['A-2', 'A-10', 'B-1']);
  });
});

describe('categories', () => {
  it('separates movies from live events and filters by tab', () => {
    expect(EVENTS.filter(isMovie)).toHaveLength(2);
    expect(filterByTab(EVENTS, 'all').map((e) => e.id)).toEqual(['e-concert-1', 'e-sport-1', 'e-comedy-1']);
    expect(filterByTab(EVENTS, 'sports').map((e) => e.id)).toEqual(['e-sport-1']);
    expect(filterByTab(EVENTS, 'more')).toEqual([]);
    expect(EVENT_TABS[0]!.key).toBe('all');
  });

  it('puts unknown categories under "more"', () => {
    const odd = { id: 'x', title: 'Pottery', category: 'WORKSHOP', language: null, durationMinutes: 60 };
    expect(filterByTab([...EVENTS, odd], 'more')).toEqual([odd]);
  });

  it('searches title, category and language case-insensitively', () => {
    expect(searchEvents(EVENTS, 'inter').map((e) => e.id)).toEqual(['e-movie-1']);
    expect(searchEvents(EVENTS, 'hindi')).toHaveLength(2);
    expect(searchEvents(EVENTS, '  ')).toHaveLength(EVENTS.length);
  });

  it('lists distinct languages', () => {
    expect(languagesOf(EVENTS)).toEqual(['English', 'Hindi']);
  });
});

describe('poster', () => {
  it('is deterministic per title', () => {
    expect(posterArt('Interstellar', 'MOVIE')).toEqual(posterArt('Interstellar', 'MOVIE'));
    expect(hashString('a')).not.toBe(hashString('b'));
  });

  it('derives initials and category glyphs', () => {
    expect(initialsOf('the dark knight')).toBe('TD');
    expect(initialsOf('!!!')).toBe('?');
    expect(posterArt('X', 'MOVIE').glyph).toBe('🎬');
    expect(posterArt('X', 'unknown').glyph).toBe('🎟️');
  });
});

describe('idempotency keys', () => {
  it('reuses one key per booking intent regardless of seat order', () => {
    const k1 = idempotencyKeyFor('show-1', ['A-2', 'A-1']);
    const k2 = idempotencyKeyFor('show-1', ['A-1', 'A-2']);
    expect(k1).toBe(k2);
    expect(k1).toMatch(/^[A-Za-z0-9-]{16,64}$/); // booking-service validation pattern
  });

  it('issues a new key for a different intent or after clearing', () => {
    const k1 = idempotencyKeyFor('show-1', ['A-1']);
    expect(idempotencyKeyFor('show-1', ['A-2'])).not.toBe(k1);
    clearIdempotencyKey('show-1', ['A-1']);
    expect(idempotencyKeyFor('show-1', ['A-1'])).not.toBe(k1);
  });

  it('fingerprints de-duplicated, sorted seats', () => {
    expect(intentFingerprint('s', ['B-1', 'A-1', 'A-1'])).toBe('s|A-1,B-1');
  });
});
