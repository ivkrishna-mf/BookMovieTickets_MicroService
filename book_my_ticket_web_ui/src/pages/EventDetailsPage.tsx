import { motion } from 'framer-motion';
import { ArrowLeft, Building2, Clock, Languages } from 'lucide-react';
import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import type { ShowSummary } from '../api/types';
import { Poster } from '../components/Poster';
import { EmptyState, ErrorState, Spinner } from '../components/States';
import { useCity } from '../hooks/city';
import { useEventDetails } from '../hooks/queries';
import { useTilt } from '../hooks/useTilt';
import { dayKey, dayLabel, formatDuration, formatTime, titleCase } from '../lib/format';

export const BOOKABLE_SHOW_STATUSES = ['SCHEDULED', 'OPEN'];

interface VenueGroup {
  venueId: string;
  venueName: string;
  city: string;
  shows: ShowSummary[];
}

export function groupShowsByDay(shows: ShowSummary[]): Map<string, VenueGroup[]> {
  const byDay = new Map<string, Map<string, VenueGroup>>();
  [...shows]
    .filter((s) => s.status !== 'CANCELLED')
    .sort((a, b) => a.startTime.localeCompare(b.startTime))
    .forEach((show) => {
      const day = dayKey(show.startTime);
      const venues = byDay.get(day) ?? new Map<string, VenueGroup>();
      const group = venues.get(show.venueId) ?? { venueId: show.venueId, venueName: show.venueName, city: show.city, shows: [] };
      group.shows.push(show);
      venues.set(show.venueId, group);
      byDay.set(day, venues);
    });
  return new Map([...byDay.entries()].map(([day, venues]) => [day, [...venues.values()]]));
}

export function EventDetailsPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const { city } = useCity();
  const { data: event, isLoading, error, refetch } = useEventDetails(id);
  const tilt = useTilt(10);
  const [cityOnly, setCityOnly] = useState(true);

  const shows = useMemo(() => {
    const all = event?.upcomingShows ?? [];
    const inCity = all.filter((s) => s.city.toLowerCase() === city.toLowerCase());
    return cityOnly && inCity.length ? inCity : all;
  }, [event, city, cityOnly]);
  const days = useMemo(() => groupShowsByDay(shows), [shows]);
  const dayKeys = [...days.keys()];
  const [selectedDay, setSelectedDay] = useState<string | null>(null);
  const activeDay = selectedDay && days.has(selectedDay) ? selectedDay : dayKeys[0];
  const hasOtherCities = !!event?.upcomingShows.some((s) => s.city.toLowerCase() !== city.toLowerCase());

  if (isLoading) return <Spinner label="Loading event" />;
  if (error || !event) return <ErrorState error={error} onRetry={() => void refetch()} />;

  return (
    <div className="stack-xl">
      <button type="button" className="link-arrow back-link" onClick={() => navigate(-1)}>
        <ArrowLeft size={16} aria-hidden /> Back
      </button>

      <section className="detail-hero">
        <motion.div
          className="detail-hero__poster"
          style={{ rotateX: tilt.rotateX, rotateY: tilt.rotateY, transformPerspective: 1000 }}
          onPointerMove={tilt.onPointerMove}
          onPointerLeave={tilt.onPointerLeave}
          initial={{ opacity: 0, rotateY: -50, z: -200 }}
          animate={{ opacity: 1, rotateY: 0, z: 0 }}
          transition={{ type: 'spring', stiffness: 90, damping: 16 }}
        >
          <Poster title={event.title} category={event.category} size="lg" />
        </motion.div>
        <motion.div className="detail-hero__copy" initial={{ opacity: 0, x: 40 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.1 }}>
          <span className="chip">{titleCase(event.category)}</span>
          <h1>{event.title}</h1>
          <p className="detail-hero__meta">
            {event.language && <span><Languages size={16} aria-hidden /> {event.language}</span>}
            {event.durationMinutes > 0 && <span><Clock size={16} aria-hidden /> {formatDuration(event.durationMinutes)}</span>}
          </p>
          {event.description && <p className="detail-hero__desc">{event.description}</p>}
        </motion.div>
      </section>

      <section className="stack-md" aria-labelledby="showtimes">
        <header className="section-head">
          <h2 id="showtimes">Showtimes</h2>
          {hasOtherCities && (
            <label className="toggle">
              <input type="checkbox" checked={cityOnly} onChange={(e) => setCityOnly(e.target.checked)} />
              <span className="toggle__track" aria-hidden><span className="toggle__thumb" /></span>
              Only {city}
            </label>
          )}
        </header>

        {!dayKeys.length ? (
          <EmptyState title="No upcoming shows">
            <p>Check back soon — new shows are added all the time.</p>
          </EmptyState>
        ) : (
          <>
            <div className="date-strip" role="tablist" aria-label="Show dates">
              {dayKeys.map((key) => {
                const label = dayLabel(key);
                const active = key === activeDay;
                return (
                  <motion.button
                    key={key}
                    role="tab"
                    aria-selected={active}
                    className={`date-pill ${active ? 'date-pill--active' : ''}`}
                    onClick={() => setSelectedDay(key)}
                    whileHover={{ y: -4, rotateX: 12 }}
                    whileTap={{ scale: 0.94 }}
                  >
                    <small>{label.weekday}</small>
                    <strong>{label.day}</strong>
                    <small>{label.month}</small>
                  </motion.button>
                );
              })}
            </div>

            <motion.ul key={activeDay} className="venue-list" initial="hidden" animate="show" variants={{ show: { transition: { staggerChildren: 0.06 } } }}>
              {(days.get(activeDay!) ?? []).map((venue) => (
                <motion.li
                  key={venue.venueId}
                  className="venue-card glass"
                  variants={{ hidden: { opacity: 0, y: 20, rotateX: -20 }, show: { opacity: 1, y: 0, rotateX: 0 } }}
                >
                  <div className="venue-card__name">
                    <Building2 size={18} aria-hidden />
                    <div>
                      <strong>{venue.venueName}</strong>
                      <small>{venue.city}</small>
                    </div>
                  </div>
                  <div className="showtimes">
                    {venue.shows.map((show) =>
                      BOOKABLE_SHOW_STATUSES.includes(show.status) ? (
                        <Link key={show.id} to={`/shows/${show.id}`} className="showtime" aria-label={`Book ${formatTime(show.startTime)} show at ${venue.venueName}`}>
                          {formatTime(show.startTime)}
                        </Link>
                      ) : (
                        <span key={show.id} className="showtime showtime--disabled" title={titleCase(show.status)}>
                          {formatTime(show.startTime)}
                        </span>
                      ),
                    )}
                  </div>
                </motion.li>
              ))}
            </motion.ul>
          </>
        )}
      </section>
    </div>
  );
}
