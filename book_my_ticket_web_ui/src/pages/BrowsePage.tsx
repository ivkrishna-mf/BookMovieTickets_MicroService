import { AnimatePresence, motion } from 'framer-motion';
import { Search, X } from 'lucide-react';
import { useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { EventCard } from '../components/EventCard';
import { EmptyState, ErrorState, SkeletonGrid } from '../components/States';
import { useCity } from '../hooks/city';
import { useCityEvents } from '../hooks/queries';
import { EVENT_TABS, filterByTab, isMovie, languagesOf, searchEvents } from '../lib/categories';

export function BrowsePage({ kind }: { kind: 'movies' | 'events' }) {
  const { city } = useCity();
  const { data, isLoading, error, refetch } = useCityEvents();
  const [params, setParams] = useSearchParams();
  const query = params.get('q') ?? '';
  const tab = params.get('tab') ?? 'all';
  const language = params.get('lang') ?? '';

  const update = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    setParams(next, { replace: true });
  };

  const base = useMemo(() => {
    const all = data ?? [];
    return kind === 'movies' ? all.filter(isMovie) : filterByTab(all, tab);
  }, [data, kind, tab]);

  const languages = useMemo(() => languagesOf(base), [base]);
  const results = useMemo(() => {
    const searched = searchEvents(base, query);
    return language ? searched.filter((e) => e.language === language) : searched;
  }, [base, query, language]);

  return (
    <div className="stack-lg">
      <header className="page-head">
        <div>
          <span className="eyebrow">{city}</span>
          <h1>{kind === 'movies' ? 'Movies' : 'Events'}</h1>
        </div>
        <label className="search">
          <Search size={18} aria-hidden />
          <input
            type="search"
            placeholder={kind === 'movies' ? 'Search movies, languages…' : 'Search concerts, sports, comedy…'}
            value={query}
            onChange={(e) => update('q', e.target.value)}
            aria-label={`Search ${kind}`}
          />
          {query && (
            <button className="icon-btn" onClick={() => update('q', '')} aria-label="Clear search">
              <X size={16} />
            </button>
          )}
        </label>
      </header>

      {kind === 'events' && (
        <div className="segmented" role="tablist" aria-label="Event categories">
          {EVENT_TABS.map((t) => (
            <button
              key={t.key}
              role="tab"
              aria-selected={tab === t.key}
              className={`segmented__item ${tab === t.key ? 'segmented__item--active' : ''}`}
              onClick={() => update('tab', t.key === 'all' ? '' : t.key)}
            >
              {tab === t.key && <motion.span layoutId="segment-pill" className="segmented__pill" />}
              <span>{t.label}</span>
            </button>
          ))}
        </div>
      )}

      {languages.length > 1 && (
        <div className="chips" role="group" aria-label="Filter by language">
          <button className={`chip ${!language ? 'chip--active' : ''}`} onClick={() => update('lang', '')} aria-pressed={!language}>
            All languages
          </button>
          {languages.map((l) => (
            <button key={l} className={`chip ${language === l ? 'chip--active' : ''}`} onClick={() => update('lang', l)} aria-pressed={language === l}>
              {l}
            </button>
          ))}
        </div>
      )}

      {isLoading && <SkeletonGrid />}
      {error && <ErrorState error={error} onRetry={() => void refetch()} />}
      {!isLoading && !error && !results.length && (
        <EmptyState title={query ? `No matches for “${query}”` : `No ${kind} in ${city} right now`}>
          <p>Try a different search, category or city.</p>
        </EmptyState>
      )}

      <motion.div layout className="card-grid">
        <AnimatePresence mode="popLayout">
          {results.map((event, i) => (
            <motion.div
              key={event.id}
              layout
              initial={{ opacity: 0, scale: 0.9 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.85, rotateY: 40 }}
              transition={{ type: 'spring', stiffness: 260, damping: 24 }}
            >
              <EventCard event={event} index={i} />
            </motion.div>
          ))}
        </AnimatePresence>
      </motion.div>
    </div>
  );
}
