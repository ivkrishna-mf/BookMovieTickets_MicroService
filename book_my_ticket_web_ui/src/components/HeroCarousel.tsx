import { motion, useReducedMotion } from 'framer-motion';
import { ChevronLeft, ChevronRight, Ticket } from 'lucide-react';
import { useCallback, useEffect, useState, type KeyboardEvent } from 'react';
import { Link } from 'react-router-dom';
import type { EventSummary } from '../api/types';
import { formatDuration, titleCase } from '../lib/format';
import { Poster } from './Poster';

const AUTO_ADVANCE_MS = 5000;

/** 3D cover-flow: the active poster faces the viewer, neighbours rotate away into depth. */
export function HeroCarousel({ events }: { events: EventSummary[] }) {
  const [active, setActive] = useState(0);
  const [paused, setPaused] = useState(false);
  const reduce = useReducedMotion();
  const count = events.length;

  const go = useCallback((delta: number) => setActive((i) => (i + delta + count) % count), [count]);

  useEffect(() => {
    if (paused || reduce || count < 2) return;
    const id = window.setInterval(() => go(1), AUTO_ADVANCE_MS);
    return () => window.clearInterval(id);
  }, [paused, reduce, count, go]);

  useEffect(() => {
    if (active >= count) setActive(0);
  }, [active, count]);

  if (!count) return null;
  const current = events[active % count]!;

  const onKeyDown = (e: KeyboardEvent) => {
    if (e.key === 'ArrowRight') go(1);
    if (e.key === 'ArrowLeft') go(-1);
  };

  return (
    <section
      className="hero"
      aria-roledescription="carousel"
      aria-label="Featured"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={() => setPaused(false)}
      onKeyDown={onKeyDown}
    >
      <div className="hero__copy">
        <motion.span key={`k-${current.id}`} className="eyebrow" initial={{ opacity: 0, x: -20 }} animate={{ opacity: 1, x: 0 }}>
          Featured · {titleCase(current.category)}
        </motion.span>
        <motion.h1
          key={`t-${current.id}`}
          className="hero__title"
          initial={{ opacity: 0, y: 30, rotateX: 50 }}
          animate={{ opacity: 1, y: 0, rotateX: 0 }}
          transition={{ type: 'spring', stiffness: 120, damping: 16 }}
        >
          {current.title}
        </motion.h1>
        <p className="hero__meta">
          {[current.language, formatDuration(current.durationMinutes)].filter(Boolean).join(' · ')}
        </p>
        <div className="hero__cta">
          <Link to={`/events/${current.id}`} className="btn btn--primary btn--lg">
            <Ticket size={18} aria-hidden /> Book tickets
          </Link>
          <div className="hero__nav">
            <button className="icon-btn icon-btn--glass" onClick={() => go(-1)} aria-label="Previous featured event">
              <ChevronLeft />
            </button>
            <button className="icon-btn icon-btn--glass" onClick={() => go(1)} aria-label="Next featured event">
              <ChevronRight />
            </button>
          </div>
        </div>
        <div className="hero__dots" role="tablist" aria-label="Choose featured event">
          {events.map((e, i) => (
            <button
              key={e.id}
              role="tab"
              aria-selected={i === active}
              aria-label={e.title}
              className={`dot ${i === active ? 'dot--active' : ''}`}
              onClick={() => setActive(i)}
            />
          ))}
        </div>
      </div>

      <div className="hero__stage" aria-live="polite">
        {events.map((event, i) => {
          let offset = i - active;
          if (offset > count / 2) offset -= count;
          if (offset < -count / 2) offset += count;
          const abs = Math.abs(offset);
          const visible = abs <= 2;
          return (
            <motion.div
              key={event.id}
              className="hero__slide"
              aria-hidden={offset !== 0}
              animate={{
                x: `${offset * 58}%`,
                z: -abs * 180,
                rotateY: offset * -38,
                scale: 1 - abs * 0.08,
                opacity: visible ? 1 - abs * 0.3 : 0,
              }}
              transition={{ type: 'spring', stiffness: 140, damping: 22 }}
              style={{ zIndex: 10 - abs, pointerEvents: visible ? 'auto' : 'none' }}
              onClick={() => (offset === 0 ? undefined : setActive(i))}
            >
              {offset === 0 ? (
                <Link to={`/events/${event.id}`} tabIndex={-1} className="hero__slide-link">
                  <Poster title={event.title} category={event.category} size="lg" />
                </Link>
              ) : (
                <Poster title={event.title} category={event.category} size="lg" />
              )}
              <div className="hero__reflection" />
            </motion.div>
          );
        })}
      </div>
    </section>
  );
}
