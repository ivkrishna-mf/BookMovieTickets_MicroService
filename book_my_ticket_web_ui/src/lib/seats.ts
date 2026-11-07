import type { SeatView } from '../api/types';

export interface SeatRow {
  row: string;
  category: string;
  seats: SeatView[];
}

export interface SeatSectionGroup {
  category: string;
  rows: SeatRow[];
}

/**
 * Groups the flat seat list into category sections (in row order) and rows (in seat index order).
 * A new section starts whenever the category changes between consecutive rows.
 */
export function groupSeats(seats: SeatView[]): SeatSectionGroup[] {
  const rowsByLabel = new Map<string, SeatView[]>();
  for (const seat of seats) {
    const list = rowsByLabel.get(seat.row) ?? [];
    list.push(seat);
    rowsByLabel.set(seat.row, list);
  }
  const rows: SeatRow[] = [...rowsByLabel.entries()]
    .sort(([a], [b]) => (a.length - b.length) || a.localeCompare(b))
    .map(([row, list]) => {
      const sorted = [...list].sort((x, y) => x.index - y.index);
      return { row, category: sorted[0]!.category, seats: sorted };
    });

  const sections: SeatSectionGroup[] = [];
  for (const row of rows) {
    const last = sections[sections.length - 1];
    if (last && last.category === row.category) last.rows.push(row);
    else sections.push({ category: row.category, rows: [row] });
  }
  return sections;
}

export function totalPrice(selected: string[], seats: SeatView[], prices: Record<string, number>): number {
  const byNumber = new Map(seats.map((s) => [s.seatNumber, s]));
  const total = selected.reduce((sum, n) => {
    const seat = byNumber.get(n);
    return sum + (seat ? prices[seat.category] ?? 0 : 0);
  }, 0);
  return Math.round(total * 100) / 100;
}

export type ToggleResult = { selected: string[]; rejected?: 'unavailable' | 'limit' };

export function toggleSeat(selected: string[], seat: SeatView, max: number): ToggleResult {
  if (selected.includes(seat.seatNumber)) return { selected: selected.filter((s) => s !== seat.seatNumber) };
  if (seat.status !== 'AVAILABLE') return { selected, rejected: 'unavailable' };
  if (selected.length >= max) return { selected, rejected: 'limit' };
  return { selected: [...selected, seat.seatNumber] };
}

/** Drops selections that became unavailable after a seat-map refresh (another user grabbed them). */
export function reconcileSelection(selected: string[], seats: SeatView[]): { kept: string[]; lost: string[] } {
  const available = new Set(seats.filter((s) => s.status === 'AVAILABLE').map((s) => s.seatNumber));
  const kept: string[] = [];
  const lost: string[] = [];
  for (const s of selected) (available.has(s) ? kept : lost).push(s);
  return { kept, lost };
}

/** Sort seat labels like A-2, A-10, B-1 naturally. */
export function sortSeatLabels(labels: string[]): string[] {
  return [...labels].sort((a, b) => a.localeCompare(b, undefined, { numeric: true }));
}
