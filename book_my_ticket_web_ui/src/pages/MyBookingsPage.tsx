import { motion } from 'framer-motion';
import { CalendarClock, ChevronLeft, ChevronRight, MapPin, Ticket } from 'lucide-react';
import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import type { BookingResponse } from '../api/types';
import { Poster } from '../components/Poster';
import { EmptyState, ErrorState, SkeletonGrid } from '../components/States';
import { StatusBadge } from '../components/StatusBadge';
import { useMyBookings } from '../hooks/queries';
import { formatDateTime, formatMoney } from '../lib/format';
import { sortSeatLabels } from '../lib/seats';

type Filter = 'all' | 'upcoming' | 'pending' | 'past';

const FILTERS: Array<{ key: Filter; label: string }> = [
  { key: 'all', label: 'All' },
  { key: 'upcoming', label: 'Upcoming' },
  { key: 'pending', label: 'Pending' },
  { key: 'past', label: 'Past & cancelled' },
];

export function matchesFilter(b: BookingResponse, filter: Filter, now = Date.now()): boolean {
  const future = new Date(b.showTime).getTime() > now;
  switch (filter) {
    case 'upcoming':
      return b.status === 'CONFIRMED' && future;
    case 'pending':
      return b.status === 'PENDING_PAYMENT' || b.status === 'PENDING_SEATS';
    case 'past':
      return !future || ['CANCELLED', 'EXPIRED', 'FAILED'].includes(b.status);
    default:
      return true;
  }
}

export function MyBookingsPage() {
  const [page, setPage] = useState(0);
  const [filter, setFilter] = useState<Filter>('all');
  const { data, isLoading, error, refetch, isFetching } = useMyBookings(page);
  const items = useMemo(() => (data?.content ?? []).filter((b) => matchesFilter(b, filter)), [data, filter]);

  return (
    <div className="stack-lg">
      <header className="page-head">
        <div>
          <span className="eyebrow">Your account</span>
          <h1>My bookings</h1>
        </div>
      </header>

      <div className="segmented" role="tablist" aria-label="Filter bookings">
        {FILTERS.map((f) => (
          <button
            key={f.key}
            role="tab"
            aria-selected={filter === f.key}
            className={`segmented__item ${filter === f.key ? 'segmented__item--active' : ''}`}
            onClick={() => setFilter(f.key)}
          >
            {filter === f.key && <motion.span layoutId="bookings-pill" className="segmented__pill" />}
            <span>{f.label}</span>
          </button>
        ))}
      </div>

      {isLoading && <SkeletonGrid count={4} />}
      {error && <ErrorState error={error} onRetry={() => void refetch()} />}
      {data && !items.length && (
        <EmptyState title="No bookings here yet" icon={<Ticket size={40} aria-hidden />}>
          <p>When you book a show it will appear here with your e-ticket.</p>
          <Link to="/movies" className="btn btn--primary">Browse movies</Link>
        </EmptyState>
      )}

      <ul className="booking-list" aria-busy={isFetching}>
        {items.map((b, i) => (
          <motion.li
            key={b.id}
            initial={{ opacity: 0, x: -30, rotateY: 20 }}
            animate={{ opacity: 1, x: 0, rotateY: 0 }}
            transition={{ delay: i * 0.05, type: 'spring', stiffness: 180, damping: 20 }}
            whileHover={{ y: -4, rotateX: 4 }}
            style={{ transformPerspective: 900 }}
          >
            <Link to={`/bookings/${b.id}`} className="booking-row glass">
              <Poster title={b.eventTitle} category="MOVIE" size="sm" showTitle={false} />
              <div className="booking-row__info">
                <strong>{b.eventTitle}</strong>
                <small><MapPin size={12} aria-hidden /> {b.venueName}</small>
                <small><CalendarClock size={12} aria-hidden /> {formatDateTime(b.showTime)}</small>
                <small>Seats {sortSeatLabels(b.seats).join(', ')}</small>
              </div>
              <div className="booking-row__side">
                <StatusBadge status={b.status} />
                <strong>{formatMoney(b.totalAmount, b.currency)}</strong>
              </div>
            </Link>
          </motion.li>
        ))}
      </ul>

      {data && data.totalPages > 1 && (
        <nav className="pager" aria-label="Bookings pages">
          <button className="icon-btn icon-btn--glass" onClick={() => setPage((p) => p - 1)} disabled={data.first} aria-label="Previous page">
            <ChevronLeft />
          </button>
          <span>Page {data.number + 1} of {data.totalPages}</span>
          <button className="icon-btn icon-btn--glass" onClick={() => setPage((p) => p + 1)} disabled={data.last} aria-label="Next page">
            <ChevronRight />
          </button>
        </nav>
      )}
    </div>
  );
}
