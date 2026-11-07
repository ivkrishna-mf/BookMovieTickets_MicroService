import { motion, useMotionTemplate } from 'framer-motion';
import { Clock, Languages } from 'lucide-react';
import { Link } from 'react-router-dom';
import type { EventSummary } from '../api/types';
import { useTilt } from '../hooks/useTilt';
import { formatDuration, titleCase } from '../lib/format';
import { Poster } from './Poster';

export function EventCard({ event, index = 0 }: { event: EventSummary; index?: number }) {
  const tilt = useTilt(14);
  const glare = useMotionTemplate`radial-gradient(circle at ${tilt.glareX} ${tilt.glareY}, rgba(255,255,255,0.28), transparent 55%)`;

  return (
    <motion.article
      className="event-card"
      initial={{ opacity: 0, y: 30, rotateX: 18 }}
      whileInView={{ opacity: 1, y: 0, rotateX: 0 }}
      viewport={{ once: true, margin: '-40px' }}
      transition={{ delay: Math.min(index, 10) * 0.05, type: 'spring', stiffness: 160, damping: 20 }}
    >
      <Link to={`/events/${event.id}`} className="event-card__link" aria-label={`${event.title}, ${titleCase(event.category)}`}>
        <motion.div
          className="event-card__tilt"
          style={{ rotateX: tilt.rotateX, rotateY: tilt.rotateY, transformPerspective: 900 }}
          onPointerMove={tilt.onPointerMove}
          onPointerLeave={tilt.onPointerLeave}
          whileTap={{ scale: 0.97 }}
        >
          <Poster title={event.title} category={event.category} showTitle={false} />
          <motion.div className="event-card__glare" style={{ background: glare }} />
          <span className="chip chip--floating">{titleCase(event.category)}</span>
        </motion.div>
        <div className="event-card__body">
          <h3 className="event-card__title">{event.title}</h3>
          <p className="event-card__meta">
            {event.language && <span><Languages size={13} aria-hidden /> {event.language}</span>}
            {event.durationMinutes > 0 && <span><Clock size={13} aria-hidden /> {formatDuration(event.durationMinutes)}</span>}
          </p>
        </div>
      </Link>
    </motion.article>
  );
}
