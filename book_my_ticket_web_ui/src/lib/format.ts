export function formatMoney(amount: number, currency: string, locale = 'en-IN'): string {
  try {
    return new Intl.NumberFormat(locale, {
      style: 'currency',
      currency,
      maximumFractionDigits: Number.isInteger(amount) ? 0 : 2,
    }).format(amount);
  } catch {
    return `${currency} ${amount.toFixed(2)}`;
  }
}

export function formatDuration(minutes: number): string {
  if (!minutes || minutes < 1) return '';
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (!h) return `${m}m`;
  return m ? `${h}h ${m}m` : `${h}h`;
}

export function formatDateTime(iso: string, locale = 'en-IN'): string {
  return new Date(iso).toLocaleString(locale, {
    weekday: 'short', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit',
  });
}

export function formatTime(iso: string, locale = 'en-IN'): string {
  return new Date(iso).toLocaleTimeString(locale, { hour: 'numeric', minute: '2-digit' });
}

/** Local calendar day key (YYYY-MM-DD) used to group shows into date tabs. */
export function dayKey(iso: string): string {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function dayLabel(key: string, now = new Date(), locale = 'en-IN'): { weekday: string; day: string; month: string } {
  const [y, m, d] = key.split('-').map(Number) as [number, number, number];
  const date = new Date(y, m - 1, d);
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const diff = Math.round((date.getTime() - today.getTime()) / 86_400_000);
  const weekday = diff === 0 ? 'Today' : diff === 1 ? 'Tomorrow' : date.toLocaleDateString(locale, { weekday: 'short' });
  return { weekday, day: String(d), month: date.toLocaleDateString(locale, { month: 'short' }) };
}

export function titleCase(value: string): string {
  return value.toLowerCase().replace(/(^|[\s_-])(\w)/g, (_, sep: string, c: string) => `${sep === '_' ? ' ' : sep}${c.toUpperCase()}`);
}

export function formatCountdown(ms: number): string {
  const total = Math.max(0, Math.ceil(ms / 1000));
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${m}:${String(s).padStart(2, '0')}`;
}
