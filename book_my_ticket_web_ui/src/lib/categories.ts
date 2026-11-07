import type { EventSummary } from '../api/types';

export interface CategoryTab {
  key: string;
  label: string;
  /** Catalog category values that belong to this tab. */
  match: string[];
}

/** Non-movie tabs shown on the Events page. Anything unmatched lands in "More". */
export const EVENT_TABS: CategoryTab[] = [
  { key: 'all', label: 'All events', match: [] },
  { key: 'concerts', label: 'Concerts', match: ['CONCERT', 'MUSIC'] },
  { key: 'sports', label: 'Sports', match: ['SPORTS', 'SPORT'] },
  { key: 'comedy', label: 'Comedy', match: ['COMEDY', 'STANDUP'] },
  { key: 'theatre', label: 'Theatre', match: ['THEATRE', 'THEATER', 'PLAY'] },
  { key: 'more', label: 'More', match: [] },
];

export const isMovie = (e: Pick<EventSummary, 'category'>) => e.category.toUpperCase() === 'MOVIE';

export function filterByTab(events: EventSummary[], tabKey: string): EventSummary[] {
  const nonMovies = events.filter((e) => !isMovie(e));
  if (tabKey === 'all') return nonMovies;
  if (tabKey === 'more') {
    const known = new Set(EVENT_TABS.flatMap((t) => t.match));
    return nonMovies.filter((e) => !known.has(e.category.toUpperCase()));
  }
  const tab = EVENT_TABS.find((t) => t.key === tabKey);
  if (!tab) return nonMovies;
  return nonMovies.filter((e) => tab.match.includes(e.category.toUpperCase()));
}

export function searchEvents(events: EventSummary[], query: string): EventSummary[] {
  const q = query.trim().toLowerCase();
  if (!q) return events;
  return events.filter((e) =>
    [e.title, e.category, e.language ?? ''].some((field) => field.toLowerCase().includes(q)),
  );
}

export function languagesOf(events: EventSummary[]): string[] {
  return [...new Set(events.map((e) => e.language).filter((l): l is string => !!l))].sort();
}
