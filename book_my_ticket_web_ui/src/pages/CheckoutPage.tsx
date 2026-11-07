import { useMutation, useQueryClient } from '@tanstack/react-query';
import { AnimatePresence, motion } from 'framer-motion';
import { CalendarClock, CircleX, CreditCard, Loader2, MapPin, PartyPopper, Timer } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { bookingApi, paymentApi, queryKeys } from '../api';
import { describeError } from '../api/http';
import type { BookingResponse } from '../api/types';
import { ErrorState, Spinner } from '../components/States';
import { StatusBadge } from '../components/StatusBadge';
import { StripePayment } from '../components/StripePayment';
import { TicketCard } from '../components/TicketCard';
import { config } from '../config';
import { useBooking } from '../hooks/queries';
import { useToast } from '../hooks/toast';
import { useCountdown } from '../hooks/useCountdown';
import { formatCountdown, formatDateTime, formatMoney } from '../lib/format';
import { sortSeatLabels } from '../lib/seats';

const CONFETTI = Array.from({ length: 28 }, (_, i) => i);

function Confetti() {
  return (
    <div className="confetti" aria-hidden>
      {CONFETTI.map((i) => (
        <motion.span
          key={i}
          className="confetti__piece"
          style={{ left: `${(i * 37) % 100}%`, background: ['#ff3d81', '#ffb547', '#7f5af0', '#2cb67d'][i % 4] }}
          initial={{ y: -20, opacity: 1, rotateX: 0, rotateZ: 0 }}
          animate={{ y: 420 + (i % 5) * 40, opacity: 0, rotateX: 720, rotateZ: (i % 2 ? 1 : -1) * 360 }}
          transition={{ duration: 2.2 + (i % 4) * 0.3, ease: 'easeOut', delay: (i % 7) * 0.05 }}
        />
      ))}
    </div>
  );
}

function CountdownRing({ remainingMs, totalMs }: { remainingMs: number; totalMs: number }) {
  const radius = 34;
  const circumference = 2 * Math.PI * radius;
  const fraction = Math.max(0, Math.min(1, remainingMs / totalMs));
  const urgent = remainingMs < 60_000;
  return (
    <div className={`countdown ${urgent ? 'countdown--urgent' : ''}`} role="timer" aria-label={`Seats held for ${formatCountdown(remainingMs)}`}>
      <svg viewBox="0 0 80 80" width="80" height="80" aria-hidden>
        <circle cx="40" cy="40" r={radius} className="countdown__track" />
        <circle
          cx="40" cy="40" r={radius}
          className="countdown__bar"
          strokeDasharray={circumference}
          strokeDashoffset={circumference * (1 - fraction)}
        />
      </svg>
      <span>{formatCountdown(remainingMs)}</span>
    </div>
  );
}

function Summary({ booking }: { booking: BookingResponse }) {
  return (
    <div className="summary">
      <h2>{booking.eventTitle}</h2>
      <p><MapPin size={14} aria-hidden /> {booking.venueName}</p>
      <p><CalendarClock size={14} aria-hidden /> {formatDateTime(booking.showTime)}</p>
      <dl className="summary__lines">
        <div><dt>Seats</dt><dd>{sortSeatLabels(booking.seats).join(', ')}</dd></div>
        <div className="summary__total"><dt>Total</dt><dd>{formatMoney(booking.totalAmount, booking.currency)}</dd></div>
      </dl>
    </div>
  );
}

export function CheckoutPage() {
  const { id = '' } = useParams();
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const [awaiting, setAwaiting] = useState(false);
  const { data: booking, isLoading, error, refetch } = useBooking(id, awaiting);
  const remaining = useCountdown(booking?.status === 'PENDING_PAYMENT' ? booking.expiresAt : null);
  const holdWindowMs = booking?.expiresAt
    ? Math.max(60_000, new Date(booking.expiresAt).getTime() - new Date(booking.createdAt).getTime())
    : 600_000;

  useEffect(() => {
    if (booking && booking.status !== 'PENDING_PAYMENT' && booking.status !== 'PENDING_SEATS') setAwaiting(false);
  }, [booking]);

  // Once the hold window elapses the saga will expire the booking: refresh to reflect it.
  useEffect(() => {
    if (remaining === 0) void refetch();
  }, [remaining, refetch]);

  const pay = useMutation({
    mutationFn: () => paymentApi.simulateSuccess(id),
    onSuccess: () => setAwaiting(true),
    onError: (e) => notify(describeError(e), 'error'),
  });

  const cancel = useMutation({
    mutationFn: () => bookingApi.cancel(id),
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKeys.booking(id), updated);
      void queryClient.invalidateQueries({ queryKey: ['myBookings'] });
      notify('Booking cancelled and seats released.', 'info');
    },
    onError: (e) => notify(describeError(e), 'error'),
  });

  const onStripeSubmitted = useCallback(() => setAwaiting(true), []);
  const onStripeError = useCallback((message: string) => notify(message, 'error'), [notify]);

  if (isLoading) return <Spinner label="Loading booking" />;
  if (error || !booking) return <ErrorState error={error} onRetry={() => void refetch()} />;

  const pending = booking.status === 'PENDING_PAYMENT';

  return (
    <div className="checkout">
      <header className="page-head">
        <div>
          <span className="eyebrow">Booking #{booking.id.slice(0, 8).toUpperCase()}</span>
          <h1>{booking.status === 'CONFIRMED' ? "You're all set!" : 'Checkout'}</h1>
        </div>
        <StatusBadge status={booking.status} />
      </header>

      <AnimatePresence mode="wait">
        {booking.status === 'CONFIRMED' && (
          <motion.section key="confirmed" className="checkout__confirmed" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
            <Confetti />
            <TicketCard booking={booking} />
            <p className="muted">
              <PartyPopper size={16} aria-hidden /> Your e-ticket has also been e-mailed to you. Show the QR code at the entrance.
            </p>
            <Link to="/bookings" className="btn btn--ghost">View all my bookings</Link>
          </motion.section>
        )}

        {(pending || booking.status === 'PENDING_SEATS') && (
          <motion.section
            key="pending"
            className="checkout__grid"
            initial={{ opacity: 0, rotateX: 12, y: 20 }}
            animate={{ opacity: 1, rotateX: 0, y: 0 }}
            exit={{ opacity: 0, rotateY: 90 }}
          >
            <div className="glass card-pad">
              <Summary booking={booking} />
            </div>
            <div className="glass card-pad pay-panel">
              {booking.status === 'PENDING_SEATS' || awaiting ? (
                <div className="pay-panel__waiting" role="status">
                  <Loader2 className="spin" size={36} aria-hidden />
                  <p>{awaiting ? 'Confirming your payment and issuing tickets…' : 'Holding your seats…'}</p>
                </div>
              ) : (
                <>
                  <div className="pay-panel__timer">
                    {remaining !== null && <CountdownRing remainingMs={remaining} totalMs={holdWindowMs} />}
                    <p><Timer size={14} aria-hidden /> Seats are held for you. Complete payment before the timer runs out.</p>
                  </div>
                  {config.paymentMode === 'stripe' && booking.paymentClientSecret ? (
                    <StripePayment clientSecret={booking.paymentClientSecret} onSubmitted={onStripeSubmitted} onError={onStripeError} />
                  ) : (
                    <>
                      <button className="btn btn--primary btn--lg btn--block" onClick={() => pay.mutate()} disabled={pay.isPending || remaining === 0}>
                        <CreditCard size={18} aria-hidden />
                        {pay.isPending ? 'Processing…' : `Pay ${formatMoney(booking.totalAmount, booking.currency)}`}
                      </button>
                      <p className="muted small">Test payment mode: simulates a successful card payment (local stack only).</p>
                    </>
                  )}
                  <button className="btn btn--ghost btn--block" onClick={() => cancel.mutate()} disabled={cancel.isPending}>
                    <CircleX size={16} aria-hidden /> {cancel.isPending ? 'Cancelling…' : 'Cancel & release seats'}
                  </button>
                </>
              )}
            </div>
          </motion.section>
        )}

        {['FAILED', 'EXPIRED', 'CANCELLED'].includes(booking.status) && (
          <motion.section key="ended" className="state glass" initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }}>
            <CircleX size={40} aria-hidden />
            <h2>
              {booking.status === 'EXPIRED' ? 'Your seat hold expired' : booking.status === 'CANCELLED' ? 'Booking cancelled' : 'Booking failed'}
            </h2>
            {booking.failureReason && <p>{booking.failureReason}</p>}
            <p className="muted">You have not been charged. The seats are back on sale.</p>
            <Link to={`/shows/${booking.showId}`} className="btn btn--primary">Pick seats again</Link>
          </motion.section>
        )}
      </AnimatePresence>
    </div>
  );
}
