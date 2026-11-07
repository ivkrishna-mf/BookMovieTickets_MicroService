import { useMemo } from 'react';
import { posterArt, posterGradient } from '../lib/poster';

interface PosterProps {
  title: string;
  category: string;
  size?: 'sm' | 'md' | 'lg';
  showTitle?: boolean;
}

/** Generative poster: deterministic gradient + category glyph + initials, layered for a 3D parallax feel. */
export function Poster({ title, category, size = 'md', showTitle = true }: PosterProps) {
  const art = useMemo(() => posterArt(title, category), [title, category]);
  return (
    <div className={`poster poster--${size}`} style={{ background: posterGradient(art) }} role="img" aria-label={`${title} poster`}>
      <div className="poster__rings" />
      <span className="poster__glyph">{art.glyph}</span>
      <span className="poster__initials">{art.initials}</span>
      {showTitle && <span className="poster__title">{title}</span>}
      <div className="poster__shine" />
    </div>
  );
}
