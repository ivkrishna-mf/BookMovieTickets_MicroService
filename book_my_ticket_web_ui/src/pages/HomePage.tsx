import { motion } from 'framer-motion';
import { ArrowRight, Clapperboard, Laugh, Mic2, Sparkles, Trophy } from 'lucide-react';
import { Link } from 'react-router-dom';
import type { EventSummary } from '../api/types';
import { EventCard } from '../components/EventCard';
import { HeroCarousel } from '../components/HeroCarousel';
import { EmptyState, ErrorState, SkeletonGrid } from '../components/States';
import { useCity } from '../hooks/city';
import { useCityEvents } from '../hooks/queries';
import { isMovie } from '../lib/categories';

const QUICK_LINKS = [
  { to: '/movies', label: 'Movies', icon: Clapperboard, hue: 'pink' },
  { to: '/events?tab=concerts', label: 'Concerts', icon: Mic2, hue: 'violet' },
  { to: '/events?tab=sports', label: 'Sports', icon: Trophy, hue: 'teal' },
  { to: '/events?tab=comedy', label: 'Comedy', icon: Laugh, hue: 'amber' },
];

function Rail({ title, to, events }: { title: string; to: string; events: EventSummary[] }) {
  if (!events.length) return null;
  return (
    <section className="rail">
      <header className="section-head">
        <h2>{title}</h2>
        <Link to={to} className="link-arrow">See all <ArrowRight size={16} aria-hidden /></Link>
      </header>
      <div className="rail__track">
        {events.slice(0, 12).map((e, i) => <EventCard key={e.id} event={e} index={i} />)}
      </div>
    </section>
  );
}

export function HomePage() {
  const { city } = useCity();
  const { data: events, isLoading, error, refetch } = useCityEvents();

  if (isLoading) return <SkeletonGrid />;
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />;

  const all = events ?? [];
  const movies = all.filter(isMovie);
  const live = all.filter((e) => !isMovie(e));
  const featured = (movies.length ? [...movies, ...live] : live).slice(0, 7);

  if (!all.length) {
    return (
      <EmptyState title={`Nothing playing in ${city} yet`} icon={<Sparkles size={40} aria-hidden />}>
        <p>Try another city, or load demo data with <code>npm run seed</code> (see README).</p>
      </EmptyState>
    );
  }

  return (
    <div className="stack-xl">
      <HeroCarousel events={featured} />

      <nav className="quick-links" aria-label="Browse by category">
        {QUICK_LINKS.map((q, i) => (
          <motion.div
            key={q.to}
            initial={{ opacity: 0, y: 20, rotateY: -30 }}
            animate={{ opacity: 1, y: 0, rotateY: 0 }}
            transition={{ delay: 0.1 + i * 0.07, type: 'spring', stiffness: 160, damping: 18 }}
            whileHover={{ y: -6, rotateX: 8, scale: 1.03 }}
            style={{ transformPerspective: 600 }}
          >
            <Link to={q.to} className={`quick-link quick-link--${q.hue}`}>
              <q.icon size={26} aria-hidden />
              <span>{q.label}</span>
            </Link>
          </motion.div>
        ))}
      </nav>

      <Rail title={`Now showing in ${city}`} to="/movies" events={movies} />
      <Rail title="Live events near you" to="/events" events={live} />
    </div>
  );
}
