import type { BookingStatus } from '../api/types';

const LABELS: Record<BookingStatus, string> = {
  PENDING_SEATS: 'Holding seats',
  PENDING_PAYMENT: 'Awaiting payment',
  CONFIRMED: 'Confirmed',
  CANCELLED: 'Cancelled',
  EXPIRED: 'Expired',
  FAILED: 'Failed',
};

const TONES: Record<BookingStatus, string> = {
  PENDING_SEATS: 'pending',
  PENDING_PAYMENT: 'pending',
  CONFIRMED: 'success',
  CANCELLED: 'muted',
  EXPIRED: 'muted',
  FAILED: 'danger',
};

export function StatusBadge({ status }: { status: BookingStatus }) {
  return <span className={`badge badge--${TONES[status] ?? 'muted'}`}>{LABELS[status] ?? status}</span>;
}
