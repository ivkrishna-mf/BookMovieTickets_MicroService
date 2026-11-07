import { useMutation, useQueryClient } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import { Building2, CalendarPlus, Clapperboard, Copy, Plus, Trash2, XCircle } from 'lucide-react';
import { useState, type FormEvent, type ReactNode } from 'react';
import { catalogApi } from '../api';
import { describeError } from '../api/http';
import type { CreateShowRequest, SeatSection } from '../api/types';
import { useCity } from '../hooks/city';
import { useCityEvents } from '../hooks/queries';
import { useToast } from '../hooks/toast';

const RECENT_KEY = 'bmt.admin.recent';

interface Recent {
  venues: Array<{ id: string; name: string; city: string }>;
  events: Array<{ id: string; title: string }>;
}

function readRecent(): Recent {
  try {
    return { venues: [], events: [], ...(JSON.parse(localStorage.getItem(RECENT_KEY) ?? '{}') as Partial<Recent>) };
  } catch {
    return { venues: [], events: [] };
  }
}

function writeRecent(r: Recent) {
  try {
    localStorage.setItem(RECENT_KEY, JSON.stringify(r));
  } catch {
    /* ignore */
  }
}

export interface SectionDraft {
  category: string;
  rows: string;
  seatsPerRow: number;
  price: number;
}

/** Converts the section editor rows into the CreateShowRequest sections + priceByCategory. */
export function buildShowPayload(
  base: Omit<CreateShowRequest, 'sections' | 'priceByCategory'>,
  drafts: SectionDraft[],
): CreateShowRequest {
  const sections: SeatSection[] = drafts.map((d) => ({
    category: d.category.trim().toUpperCase(),
    rows: d.rows.split(/[\s,]+/).map((r) => r.trim().toUpperCase()).filter(Boolean),
    seatsPerRow: Number(d.seatsPerRow),
  }));
  const priceByCategory = Object.fromEntries(drafts.map((d) => [d.category.trim().toUpperCase(), Number(d.price)]));
  return { ...base, sections, priceByCategory };
}

const PANELS = [
  { key: 'venue', label: 'Venue', icon: Building2 },
  { key: 'event', label: 'Event', icon: Clapperboard },
  { key: 'show', label: 'Show', icon: CalendarPlus },
  { key: 'cancel', label: 'Cancel show', icon: XCircle },
] as const;

type PanelKey = (typeof PANELS)[number]['key'];

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
    </label>
  );
}

function CreatedId({ id }: { id: string | null }) {
  const { notify } = useToast();
  if (!id) return null;
  return (
    <motion.div className="created-id" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} role="status">
      Created <code>{id}</code>
      <button
        type="button"
        className="icon-btn"
        aria-label="Copy id"
        onClick={() => void navigator.clipboard?.writeText(id).then(() => notify('ID copied', 'success'))}
      >
        <Copy size={14} />
      </button>
    </motion.div>
  );
}

function VenueForm({ onCreated }: { onCreated: (v: Recent['venues'][number]) => void }) {
  const { city } = useCity();
  const { notify } = useToast();
  const [form, setForm] = useState({ name: '', city, address: '', capacity: 120 });
  const [created, setCreated] = useState<string | null>(null);
  const m = useMutation({
    mutationFn: () => catalogApi.createVenue({ ...form, capacity: Number(form.capacity) }),
    onSuccess: ({ id }) => {
      setCreated(id);
      onCreated({ id, name: form.name, city: form.city });
      notify(`Venue “${form.name}” created`, 'success');
    },
    onError: (e) => notify(describeError(e), 'error'),
  });
  const submit = (e: FormEvent) => {
    e.preventDefault();
    m.mutate();
  };
  return (
    <form className="form-grid" onSubmit={submit}>
      <Field label="Name"><input required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></Field>
      <Field label="City"><input required value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })} /></Field>
      <Field label="Address"><input value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} /></Field>
      <Field label="Capacity"><input type="number" min={1} required value={form.capacity} onChange={(e) => setForm({ ...form, capacity: Number(e.target.value) })} /></Field>
      <button className="btn btn--primary" disabled={m.isPending}>{m.isPending ? 'Creating…' : 'Create venue'}</button>
      <CreatedId id={created} />
    </form>
  );
}

function EventForm({ onCreated }: { onCreated: (v: Recent['events'][number]) => void }) {
  const { notify } = useToast();
  const [form, setForm] = useState({ title: '', description: '', category: 'MOVIE', language: 'English', durationMinutes: 150 });
  const [created, setCreated] = useState<string | null>(null);
  const m = useMutation({
    mutationFn: () => catalogApi.createEvent({ ...form, durationMinutes: Number(form.durationMinutes) }),
    onSuccess: ({ id }) => {
      setCreated(id);
      onCreated({ id, title: form.title });
      notify(`Event “${form.title}” created`, 'success');
    },
    onError: (e) => notify(describeError(e), 'error'),
  });
  return (
    <form className="form-grid" onSubmit={(e) => { e.preventDefault(); m.mutate(); }}>
      <Field label="Title"><input required value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} /></Field>
      <Field label="Category">
        <select value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })}>
          {['MOVIE', 'CONCERT', 'SPORTS', 'COMEDY', 'THEATRE', 'KIDS', 'WORKSHOP'].map((c) => <option key={c}>{c}</option>)}
        </select>
      </Field>
      <Field label="Language"><input value={form.language} onChange={(e) => setForm({ ...form, language: e.target.value })} /></Field>
      <Field label="Duration (minutes)"><input type="number" min={1} required value={form.durationMinutes} onChange={(e) => setForm({ ...form, durationMinutes: Number(e.target.value) })} /></Field>
      <label className="field field--wide">
        <span>Description</span>
        <textarea rows={3} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
      </label>
      <button className="btn btn--primary" disabled={m.isPending}>{m.isPending ? 'Creating…' : 'Create event'}</button>
      <CreatedId id={created} />
    </form>
  );
}

function defaultStart(): string {
  const d = new Date(Date.now() + 2 * 86_400_000);
  d.setHours(19, 30, 0, 0);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function ShowForm({ recent }: { recent: Recent }) {
  const { notify } = useToast();
  const queryClient = useQueryClient();
  const { data: cityEvents } = useCityEvents();
  const [base, setBase] = useState({ eventId: '', venueId: '', startTime: defaultStart(), currency: 'INR', maxSeatsPerBooking: 6 });
  const [drafts, setDrafts] = useState<SectionDraft[]>([
    { category: 'SILVER', rows: 'A B C', seatsPerRow: 14, price: 220 },
    { category: 'GOLD', rows: 'D E F', seatsPerRow: 14, price: 350 },
    { category: 'RECLINER', rows: 'G', seatsPerRow: 10, price: 750 },
  ]);
  const [created, setCreated] = useState<string | null>(null);
  const eventOptions = [...recent.events, ...(cityEvents ?? []).map((e) => ({ id: e.id, title: e.title }))]
    .filter((e, i, arr) => arr.findIndex((x) => x.id === e.id) === i);

  const m = useMutation({
    mutationFn: () =>
      catalogApi.createShow(
        buildShowPayload(
          { ...base, startTime: new Date(base.startTime).toISOString(), maxSeatsPerBooking: Number(base.maxSeatsPerBooking) },
          drafts,
        ),
      ),
    onSuccess: ({ id }) => {
      setCreated(id);
      void queryClient.invalidateQueries({ queryKey: ['events'] });
      void queryClient.invalidateQueries({ queryKey: ['event', base.eventId] });
      notify('Show scheduled — the seat map is being generated', 'success');
    },
    onError: (e) => notify(describeError(e), 'error'),
  });

  const updateDraft = (i: number, patch: Partial<SectionDraft>) => setDrafts((d) => d.map((x, j) => (j === i ? { ...x, ...patch } : x)));

  return (
    <form className="form-grid" onSubmit={(e) => { e.preventDefault(); m.mutate(); }}>
      <Field label="Event ID">
        <input required list="event-options" value={base.eventId} onChange={(e) => setBase({ ...base, eventId: e.target.value })} placeholder="Pick or paste an event id" />
        <datalist id="event-options">{eventOptions.map((e) => <option key={e.id} value={e.id}>{e.title}</option>)}</datalist>
      </Field>
      <Field label="Venue ID">
        <input required list="venue-options" value={base.venueId} onChange={(e) => setBase({ ...base, venueId: e.target.value })} placeholder="Pick or paste a venue id" />
        <datalist id="venue-options">{recent.venues.map((v) => <option key={v.id} value={v.id}>{`${v.name} (${v.city})`}</option>)}</datalist>
      </Field>
      <Field label="Start time"><input type="datetime-local" required value={base.startTime} onChange={(e) => setBase({ ...base, startTime: e.target.value })} /></Field>
      <Field label="Currency"><input required pattern="[A-Z]{3}" value={base.currency} onChange={(e) => setBase({ ...base, currency: e.target.value.toUpperCase() })} /></Field>
      <Field label="Max seats per booking"><input type="number" min={1} max={20} value={base.maxSeatsPerBooking} onChange={(e) => setBase({ ...base, maxSeatsPerBooking: Number(e.target.value) })} /></Field>

      <fieldset className="sections-editor field--wide">
        <legend>Seat layout & pricing</legend>
        {drafts.map((d, i) => (
          <div key={i} className="sections-editor__row">
            <input aria-label="Category" value={d.category} onChange={(e) => updateDraft(i, { category: e.target.value })} required />
            <input aria-label="Rows" value={d.rows} onChange={(e) => updateDraft(i, { rows: e.target.value })} placeholder="A B C" required />
            <input aria-label="Seats per row" type="number" min={1} value={d.seatsPerRow} onChange={(e) => updateDraft(i, { seatsPerRow: Number(e.target.value) })} />
            <input aria-label="Price" type="number" min={0} step="0.01" value={d.price} onChange={(e) => updateDraft(i, { price: Number(e.target.value) })} />
            <button type="button" className="icon-btn" aria-label="Remove section" onClick={() => setDrafts((x) => x.filter((_, j) => j !== i))} disabled={drafts.length === 1}>
              <Trash2 size={16} />
            </button>
          </div>
        ))}
        <button type="button" className="btn btn--ghost btn--sm" onClick={() => setDrafts((x) => [...x, { category: 'PLATINUM', rows: 'H', seatsPerRow: 10, price: 500 }])}>
          <Plus size={14} aria-hidden /> Add section
        </button>
      </fieldset>

      <button className="btn btn--primary" disabled={m.isPending}>{m.isPending ? 'Scheduling…' : 'Schedule show'}</button>
      <CreatedId id={created} />
    </form>
  );
}

function CancelShowForm() {
  const { notify } = useToast();
  const [id, setId] = useState('');
  const m = useMutation({
    mutationFn: () => catalogApi.cancelShow(id.trim()),
    onSuccess: () => notify('Show cancelled', 'success'),
    onError: (e) => notify(describeError(e), 'error'),
  });
  return (
    <form className="form-grid" onSubmit={(e) => { e.preventDefault(); if (window.confirm('Cancel this show? Bookings will be refunded.')) m.mutate(); }}>
      <Field label="Show ID"><input required value={id} onChange={(e) => setId(e.target.value)} /></Field>
      <button className="btn btn--danger" disabled={m.isPending}>{m.isPending ? 'Cancelling…' : 'Cancel show'}</button>
    </form>
  );
}

export function AdminPage() {
  const [panel, setPanel] = useState<PanelKey>('venue');
  const [recent, setRecent] = useState<Recent>(readRecent);
  const remember = (patch: Partial<Recent>) =>
    setRecent((r) => {
      const next = { venues: [...(patch.venues ?? []), ...r.venues].slice(0, 20), events: [...(patch.events ?? []), ...r.events].slice(0, 20) };
      writeRecent(next);
      return next;
    });

  return (
    <div className="stack-lg">
      <header className="page-head">
        <div>
          <span className="eyebrow">Administration</span>
          <h1>Manage catalog</h1>
        </div>
      </header>

      <div className="segmented" role="tablist" aria-label="Admin sections">
        {PANELS.map((p) => (
          <button key={p.key} role="tab" aria-selected={panel === p.key} className={`segmented__item ${panel === p.key ? 'segmented__item--active' : ''}`} onClick={() => setPanel(p.key)}>
            {panel === p.key && <motion.span layoutId="admin-pill" className="segmented__pill" />}
            <p.icon size={16} aria-hidden /> <span>{p.label}</span>
          </button>
        ))}
      </div>

      <motion.section key={panel} className="glass card-pad" initial={{ opacity: 0, rotateY: -12, x: 30 }} animate={{ opacity: 1, rotateY: 0, x: 0 }} style={{ transformPerspective: 1200 }}>
        {panel === 'venue' && <VenueForm onCreated={(v) => remember({ venues: [v] })} />}
        {panel === 'event' && <EventForm onCreated={(e) => remember({ events: [e] })} />}
        {panel === 'show' && <ShowForm recent={recent} />}
        {panel === 'cancel' && <CancelShowForm />}
      </motion.section>
    </div>
  );
}
