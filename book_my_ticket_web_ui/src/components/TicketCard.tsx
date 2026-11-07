import { motion } from 'framer-motion';
import { CalendarClock, MapPin, RotateCw } from 'lucide-react';
import QRCode from 'qrcode';
import { useEffect, useState } from 'react';
import type { BookingResponse } from '../api/types';
import { formatDateTime, formatMoney } from '../lib/format';
import { posterArt, posterGradient } from '../lib/poster';
import { sortSeatLabels } from '../lib/seats';

/** Confirmed e-ticket: a 3D card that flips between the summary (front) and the entry QR code (back). */
export function TicketCard({ booking }: { booking: BookingResponse }) {
  const [flipped, setFlipped] = useState(false);
  const [qr, setQr] = useState<string | null>(null);
  const art = posterArt(booking.eventTitle, 'MOVIE');

  useEffect(() => {
    let cancelled = false;
    if (!booking.ticketCode) return;
    QRCode.toDataURL(booking.ticketCode, { margin: 1, width: 240, color: { dark: '#120a24', light: '#ffffff' } })
      .then((url) => !cancelled && setQr(url))
      .catch(() => !cancelled && setQr(null));
    return () => {
      cancelled = true;
    };
  }, [booking.ticketCode]);

  return (
    <div className="ticket-scene">
      <motion.button
        type="button"
        className="ticket"
        onClick={() => setFlipped((f) => !f)}
        aria-label={flipped ? 'Show ticket details' : 'Show entry QR code'}
        initial={{ rotateY: -90, opacity: 0 }}
        animate={{ rotateY: flipped ? 180 : 0, opacity: 1 }}
        transition={{ type: 'spring', stiffness: 90, damping: 15 }}
      >
        <div className="ticket__face ticket__face--front" style={{ background: posterGradient(art) }}>
          <span className="eyebrow">Admit {booking.seats.length}</span>
          <h3>{booking.eventTitle}</h3>
          <p><MapPin size={14} aria-hidden /> {booking.venueName}</p>
          <p><CalendarClock size={14} aria-hidden /> {formatDateTime(booking.showTime)}</p>
          <div className="ticket__perforation" aria-hidden />
          <div className="ticket__footer">
            <div>
              <small>Seats</small>
              <strong>{sortSeatLabels(booking.seats).join(', ')}</strong>
            </div>
            <div>
              <small>Paid</small>
              <strong>{formatMoney(booking.totalAmount, booking.currency)}</strong>
            </div>
          </div>
          <span className="ticket__hint"><RotateCw size={12} aria-hidden /> Tap for QR</span>
        </div>
        <div className="ticket__face ticket__face--back">
          {qr ? <img src={qr} alt={`Entry QR code for booking ${booking.id}`} width={200} height={200} /> : <div className="skeleton skeleton--qr" />}
          <code className="ticket__code">{booking.ticketCode}</code>
          <span className="ticket__hint"><RotateCw size={12} aria-hidden /> Tap to flip back</span>
        </div>
      </motion.button>
    </div>
  );
}
