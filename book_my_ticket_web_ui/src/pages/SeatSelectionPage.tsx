import { useMutation, useQueryClient } from '@tanstack/react-query';
import { AnimatePresence, motion } from 'framer-motion';
import { ArrowLeft, CalendarClock, MapPin, Ticket } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { bookingApi, queryKeys } from '../api';
import { ApiError, describeError } from '../api/http';
import type { SeatView } from '../api/types';
import { useAuth } from '../auth/AuthProvider';
import { SeatMap } from '../components/SeatMap';
import { ErrorState, Spinner } from '../components/States';
import { useSeatMap, useShow } from '../hooks/queries';
import { useToast } from '../hooks/toast';
import { formatDateTime, formatMoney } from '../lib/format';
import { clearIdempotencyKey, idempotencyKeyFor } from '../lib/idempotency';
import { reconcileSelection, sortSeatLabels, toggleSeat, totalPrice } from '../lib/seats';
import { BOOKABLE_SHOW_STATUSES } from './EventDetailsPage';

const selectionKey = (showId: string) => `bmt.sel.${showId}`;

function readSelection(showId: string): string[] {
  try {
    const raw = sessionStorage.getItem(selectionKey(showId));
    return raw ? (JSON.parse(raw) as string[]) : [];
  } catch {
    return [];
  }
}

export function SeatSelectionPage() {
  const { showId = '' } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { isAuthenticated, login } = useAuth();
  const { notify } = useToast();
  const show = useShow(showId);
  const seatMap = useSeatMap(showId);
  // Selection survives the sign-in redirect so users don't lose their seats.
  const [selected, setSelected] = useState<string[]>(() => readSelection(showId));
  const [conflicted, setConflicted] = useState<string[]>([]);
  const lastNotified = useRef<string>('');

  useEffect(() => {
    try {
      sessionStorage.setItem(selectionKey(showId), JSON.stringify(selected));
    } catch {
      /* ignore */
    }
  }, [selected, showId]);

  // Another user may grab seats while we're choosing: drop them and tell the user.
  useEffect(() => {
    if (!seatMap.data) return;
    const { kept, lost } = reconcileSelection(selected, seatMap.data.seats);
    if (lost.length) {
      setSelected(kept);
      const signature = lost.join(',');
      if (lastNotified.current !== signature) {
        lastNotified.current = signature;
        notify(`Seat${lost.length > 1 ? 's' : ''} ${sortSeatLabels(lost).join(', ')} just got taken.`, 'info');
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [seatMap.data]);

  const book = useMutation({
    mutationFn: (seats: string[]) => bookingApi.create(showId, seats, idempotencyKeyFor(showId, seats)),
    onSuccess: (booking, seats) => {
      clearIdempotencyKey(showId, seats);
      sessionStorage.removeItem(selectionKey(showId));
      queryClient.setQueryData(queryKeys.booking(booking.id), booking);
      void queryClient.invalidateQueries({ queryKey: queryKeys.seatMap(showId) });
      navigate(`/bookings/${booking.id}`);
    },
    onError: (error, seats) => {
      if (error instanceof ApiError && error.status === 409) {
        clearIdempotencyKey(showId, seats);
        const unavailable = (error.problem.unavailableSeats as string[] | undefined) ?? [];
        setConflicted(unavailable);
        setSelected((s) => s.filter((x) => !unavailable.includes(x)));
        window.setTimeout(() => setConflicted([]), 2500);
        void seatMap.refetch();
      } else if (error instanceof ApiError && error.isUnauthorized) {
        void login(`/shows/${showId}`);
        return;
      }
      if (error instanceof ApiError && error.code === 'ACTIVE_BOOKING_EXISTS') {
        // One active booking per user per show: take the user to the one already in progress.
        void bookingApi.mine(0, 50).then((page) => {
          const active = page.content.find((b) => b.showId === showId && (b.status === 'PENDING_PAYMENT' || b.status === 'PENDING_SEATS'));
          if (active) {
            notify('You already have a booking in progress for this show — taking you there.', 'info');
            navigate(`/bookings/${active.id}`);
          } else {
            notify(describeError(error), 'error');
          }
        }, () => notify(describeError(error), 'error'));
        return;
      }
      notify(describeError(error), 'error');
    },
  });

  const prices = useMemo(() => show.data?.priceByCategory ?? {}, [show.data]);
  const seats = useMemo(() => seatMap.data?.seats ?? [], [seatMap.data]);
  const total = useMemo(() => totalPrice(selected, seats, prices), [selected, seats, prices]);

  if (show.isLoading || seatMap.isLoading) return <Spinner label="Loading seat map" />;
  if (show.error || !show.data) return <ErrorState error={show.error} onRetry={() => void show.refetch()} />;
  if (seatMap.error || !seatMap.data) return <ErrorState error={seatMap.error} onRetry={() => void seatMap.refetch()} />;

  const s = show.data;
  const bookable = BOOKABLE_SHOW_STATUSES.includes(s.status);

  const onSeatClick = (seat: SeatView) => {
    const result = toggleSeat(selected, seat, s.maxSeatsPerBooking);
    if (result.rejected === 'limit') notify(`You can book up to ${s.maxSeatsPerBooking} seats per booking.`, 'info');
    setSelected(result.selected);
  };

  const onBook = () => {
    if (!isAuthenticated) {
      void login(`/shows/${showId}`);
      return;
    }
    book.mutate(sortSeatLabels(selected));
  };

  return (
    <div className="stack-lg seat-page">
      <header className="show-head glass">
        <Link to={`/events/${s.eventId}`} className="icon-btn icon-btn--glass" aria-label="Back to event">
          <ArrowLeft size={18} />
        </Link>
        <div>
          <h1>{s.eventTitle}</h1>
          <p className="show-head__meta">
            <span><MapPin size={14} aria-hidden /> {s.venueName}, {s.city}</span>
            <span><CalendarClock size={14} aria-hidden /> {formatDateTime(s.startTime)}</span>
          </p>
        </div>
        <div className="show-head__avail">
          <strong>{seatMap.data.available}</strong>
          <small>seats left</small>
        </div>
      </header>

      {!bookable && <div className="banner banner--warn" role="status">This show is {s.status.toLowerCase()} and is not accepting bookings.</div>}

      <SeatMap seats={seats} selected={selected} prices={prices} currency={s.currency} onSeatClick={onSeatClick} conflicted={conflicted} />

      <AnimatePresence>
        {selected.length > 0 && (
          <motion.aside
            className="checkout-bar glass"
            initial={{ y: 120, opacity: 0, rotateX: 60 }}
            animate={{ y: 0, opacity: 1, rotateX: 0 }}
            exit={{ y: 120, opacity: 0 }}
            transition={{ type: 'spring', stiffness: 260, damping: 26 }}
            aria-label="Booking summary"
          >
            <div className="checkout-bar__seats">
              <small>{selected.length} / {s.maxSeatsPerBooking} seats</small>
              <div className="checkout-bar__chips">
                <AnimatePresence initial={false}>
                  {sortSeatLabels(selected).map((n) => (
                    <motion.span key={n} className="chip chip--active" layout initial={{ scale: 0 }} animate={{ scale: 1 }} exit={{ scale: 0 }}>
                      {n}
                    </motion.span>
                  ))}
                </AnimatePresence>
              </div>
            </div>
            <div className="checkout-bar__total">
              <small>Total</small>
              <motion.strong key={total} initial={{ y: -10, opacity: 0 }} animate={{ y: 0, opacity: 1 }}>
                {formatMoney(total, s.currency)}
              </motion.strong>
            </div>
            <button className="btn btn--primary btn--lg" onClick={onBook} disabled={!bookable || book.isPending}>
              <Ticket size={18} aria-hidden />
              {book.isPending ? 'Holding seats…' : isAuthenticated ? 'Book now' : 'Sign in to book'}
            </button>
          </motion.aside>
        )}
      </AnimatePresence>
    </div>
  );
}
