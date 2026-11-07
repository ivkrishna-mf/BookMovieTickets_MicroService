/**
 * The catalog has no artwork, so every event gets deterministic generative "poster art":
 * the same title always produces the same gradient, angle and glyph, on every device.
 */

export interface PosterArt {
  from: string;
  via: string;
  to: string;
  angle: number;
  glyph: string;
  initials: string;
}

const PALETTES: Array<[string, string, string]> = [
  ['#ff3d81', '#a23cff', '#2b1055'],
  ['#ffb547', '#ff5e62', '#3a0c3d'],
  ['#00d2ff', '#3a7bd5', '#1a1446'],
  ['#43e97b', '#38a3a5', '#10243e'],
  ['#f857a6', '#ff5858', '#2d0b2e'],
  ['#7f5af0', '#2cb67d', '#16161a'],
  ['#fda085', '#f6d365', '#4a1942'],
  ['#4facfe', '#a18cd1', '#1b1035'],
];

const GLYPHS: Record<string, string> = {
  MOVIE: '🎬',
  CONCERT: '🎤',
  MUSIC: '🎵',
  SPORTS: '🏏',
  SPORT: '🏟️',
  COMEDY: '🎭',
  THEATRE: '🎭',
  THEATER: '🎭',
  KIDS: '🎈',
  WORKSHOP: '🛠️',
};

/** FNV-1a 32-bit hash: tiny, fast, well distributed for short strings. */
export function hashString(value: string): number {
  let hash = 0x811c9dc5;
  for (let i = 0; i < value.length; i++) {
    hash ^= value.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193);
  }
  return hash >>> 0;
}

export function initialsOf(title: string): string {
  const words = title.replace(/[^\p{L}\p{N}\s]/gu, ' ').split(/\s+/).filter(Boolean);
  if (!words.length) return '?';
  return words.slice(0, 2).map((w) => w[0]!.toUpperCase()).join('');
}

export function categoryGlyph(category: string): string {
  return GLYPHS[category.toUpperCase()] ?? '🎟️';
}

export function posterArt(title: string, category: string): PosterArt {
  const h = hashString(`${title}::${category}`);
  const [from, via, to] = PALETTES[h % PALETTES.length]!;
  return { from, via, to, angle: 120 + (h % 120), glyph: categoryGlyph(category), initials: initialsOf(title) };
}

export function posterGradient(art: PosterArt): string {
  return `linear-gradient(${art.angle}deg, ${art.from} 0%, ${art.via} 48%, ${art.to} 100%)`;
}
