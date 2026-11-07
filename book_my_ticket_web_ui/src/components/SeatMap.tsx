import { motion, useReducedMotion } from 'framer-motion';
import { useMemo } from 'react';
import type { SeatView } from '../api/types';
import { formatMoney, titleCase } from '../lib/format';
import { groupSeats } from '../lib/seats';

interface SeatMapProps {
  seats: SeatView[];
  selected: string[];
  prices: Record<string, number>;
  currency: string;
  onSeatClick: (seat: SeatView) => void;
  /** Seats the server reported as just taken - flashed to draw attention. */
  conflicted?: string[];
}

export function SeatMap({ seats, selected, prices, currency, onSeatClick, conflicted = [] }: SeatMapProps) {
  const sections = useMemo(() => groupSeats(seats), [seats]);
  const reduce = useReducedMotion();
  const selectedSet = useMemo(() => new Set(selected), [selected]);
  const conflictSet = useMemo(() => new Set(conflicted), [conflicted]);

  return (
    <div className="auditorium">
      <div className="auditorium__stage">
        <motion.div
          className="auditorium__floor"
          initial={reduce ? false : { rotateX: 55, opacity: 0 }}
          animate={{ rotateX: 18, opacity: 1 }}
          transition={{ type: 'spring', stiffness: 60, damping: 16 }}
        >
          {sections.map((section) => (
            <section key={section.category + section.rows[0]?.row} className="seat-section" aria-label={`${titleCase(section.category)} section`}>
              <header className="seat-section__header">
                <span>{titleCase(section.category)}</span>
                <span className="seat-section__price">
                  {prices[section.category] !== undefined ? formatMoney(prices[section.category]!, currency) : '—'}
                </span>
              </header>
              {section.rows.map((row) => {
                const mid = Math.ceil(row.seats.length / 2);
                return (
                  <div key={row.row} className="seat-row" role="group" aria-label={`Row ${row.row}`}>
                    <span className="seat-row__label" aria-hidden>{row.row}</span>
                    <div className="seat-row__seats">
                      {row.seats.map((seat, i) => {
                        const isSelected = selectedSet.has(seat.seatNumber);
                        const isTaken = seat.status !== 'AVAILABLE';
                        const state = isSelected ? 'selected' : isTaken ? 'taken' : 'free';
                        return (
                          <motion.button
                            key={seat.seatNumber}
                            type="button"
                            className={`seat seat--${state} ${conflictSet.has(seat.seatNumber) ? 'seat--conflict' : ''} ${i === mid ? 'seat--aisle' : ''}`}
                            data-category={seat.category}
                            aria-label={`Seat ${seat.seatNumber}, ${titleCase(seat.category)}, ${isSelected ? 'selected' : isTaken ? 'unavailable' : 'available'}`}
                            aria-pressed={isSelected}
                            disabled={isTaken && !isSelected}
                            onClick={() => onSeatClick(seat)}
                            whileHover={isTaken || reduce ? undefined : { y: -4, scale: 1.18, z: 20 }}
                            whileTap={isTaken ? undefined : { scale: 0.85 }}
                            animate={isSelected ? { y: -3, rotateX: -20 } : { y: 0, rotateX: 0 }}
                            transition={{ type: 'spring', stiffness: 500, damping: 22 }}
                          >
                            <span className="seat__num" aria-hidden>{seat.index}</span>
                          </motion.button>
                        );
                      })}
                    </div>
                    <span className="seat-row__label" aria-hidden>{row.row}</span>
                  </div>
                );
              })}
            </section>
          ))}
        </motion.div>
        <div className="screen" aria-hidden>
          <div className="screen__surface" />
          <span>All eyes this way</span>
        </div>
      </div>

      <ul className="seat-legend" aria-label="Seat legend">
        <li><span className="seat seat--free seat--legend" /> Available</li>
        <li><span className="seat seat--selected seat--legend" /> Selected</li>
        <li><span className="seat seat--taken seat--legend" /> Sold / held</li>
      </ul>
    </div>
  );
}
