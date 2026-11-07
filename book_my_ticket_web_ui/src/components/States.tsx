import { motion } from 'framer-motion';
import { RefreshCw, SearchX, ServerCrash } from 'lucide-react';
import type { ReactNode } from 'react';
import { describeError } from '../api/http';

export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="spinner" role="status" aria-label={label}>
      <div className="spinner__cube">
        {Array.from({ length: 6 }, (_, i) => <span key={i} className={`spinner__face spinner__face--${i}`} />)}
      </div>
    </div>
  );
}

export function SkeletonGrid({ count = 8 }: { count?: number }) {
  return (
    <div className="card-grid" aria-busy="true" aria-label="Loading events">
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="skeleton-card">
          <div className="skeleton skeleton--poster" />
          <div className="skeleton skeleton--line" />
          <div className="skeleton skeleton--line skeleton--short" />
        </div>
      ))}
    </div>
  );
}

export function ErrorState({ error, onRetry, title = 'We hit a snag' }: { error: unknown; onRetry?: () => void; title?: string }) {
  return (
    <motion.div className="state state--error" role="alert" initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }}>
      <ServerCrash size={40} aria-hidden />
      <h2>{title}</h2>
      <p>{describeError(error)}</p>
      {onRetry && (
        <button className="btn btn--ghost" onClick={onRetry}>
          <RefreshCw size={16} aria-hidden /> Try again
        </button>
      )}
    </motion.div>
  );
}

export function EmptyState({ title, children, icon }: { title: string; children?: ReactNode; icon?: ReactNode }) {
  return (
    <motion.div className="state" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
      {icon ?? <SearchX size={40} aria-hidden />}
      <h2>{title}</h2>
      {children}
    </motion.div>
  );
}
