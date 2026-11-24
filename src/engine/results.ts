/** Browse saved trips without provider calls: port of flightcore/result_browser.py. */
import { details as airportDetails } from './airports';
import { SearchValidationError, type Segment, type Trip, pythonFloat, round2 } from './flexible';

export const PAGE_SIZE = 50;
const MAX_EXTRA_EUROS = 20;
const MAX_EXTRA_SHARE = 0.5;
const MAX_EUROS_PER_HOUR = 3;

export type Sort = 'price' | 'useful' | 'duration' | 'departure';

function number(value: unknown, label: string): number | null {
  if (value === null || value === undefined || value === '') return null;
  const result = pythonFloat(value);
  if (result === null || !Number.isFinite(result) || result < 0) {
    throw new SearchValidationError(`${label}: indica un numero positivo.`);
  }
  return result;
}

function code(value: unknown, length: number): string {
  const v = String(value ?? '').toUpperCase();
  if (v && !new RegExp(`^[A-Z0-9]{${length}}$`).test(v)) {
    throw new SearchValidationError('Codice aeroporto o compagnia non valido.');
  }
  return v;
}

function codes(value: unknown): string[] {
  return String(value ?? '')
    .split(',')
    .map((c) => c.trim())
    .filter(Boolean)
    .map((c) => code(c, 3));
}

export interface FilterArgs {
  max_price?: string | number | null;
  min_hours?: string | number | null;
  origin?: string;
  destination?: string;
  airline?: string;
  home_before?: string;
  exclude?: string;
}

export class ResultFilters {
  constructor(
    readonly max_price: number | null = null,
    readonly min_hours: number | null = null,
    readonly origin = '',
    readonly destination = '',
    readonly airline = '',
    readonly home_before = '',
    readonly excluded: string[] = [],
  ) {}

  static parse(args: FilterArgs = {}): ResultFilters {
    const homeBefore = args.home_before ?? '';
    if (homeBefore && !/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(homeBefore)) {
      throw new SearchValidationError('Orario di rientro non valido.');
    }
    return new ResultFilters(
      number(args.max_price, 'Prezzo massimo'),
      number(args.min_hours, 'Ore minime'),
      code(args.origin, 3),
      codes(args.destination).join(','),
      code(args.airline, 2),
      homeBefore,
      codes(args.exclude),
    );
  }

  matches(trip: Trip): boolean {
    const out = trip.outbound.segments;
    const destination = out[out.length - 1].to_code;
    return (
      (this.max_price === null || trip.price <= this.max_price) &&
      (!this.min_hours || (trip.useful_minutes || 0) >= this.min_hours * 60) &&
      (!this.origin || out[0].from_code === this.origin) &&
      (!this.destination || this.destination.split(',').includes(destination)) &&
      !this.excluded.includes(destination) &&
      (!this.airline || segments(trip).every((s) => s.airline_code === this.airline)) &&
      this.matchesReturn(trip)
    );
  }

  matchesReturn(trip: Trip): boolean {
    if (!this.home_before) return true;
    if (!trip.inbound) return false;
    const back = trip.inbound.segments;
    // Compare local calendar dates as well as the clock.
    const deadline = `${back[0].departure.slice(0, 10)}T${this.home_before}`;
    return back[back.length - 1].arrival.slice(0, 16) <= deadline;
  }
}

export function segments(trip: Trip): Segment[] {
  return [trip.outbound, trip.inbound].flatMap((leg) => (leg ? leg.segments : []));
}

type Key = (string | number)[];

export function sortKey(trip: Trip, sort: Sort | string): Key {
  if (sort === 'useful') return [-(trip.useful_minutes || 0), trip.price, trip.id];
  if (sort === 'duration') {
    const total = trip.outbound.duration_min + (trip.inbound ? trip.inbound.duration_min : 0);
    return [total, trip.price, trip.id];
  }
  if (sort === 'departure') return [trip.outbound.segments[0].departure, trip.price, trip.id];
  return [trip.price, -(trip.useful_minutes || 0), trip.id];
}

export function compareKeys(a: Key, b: Key): number {
  for (let i = 0; i < Math.min(a.length, b.length); i++) {
    if (a[i] < b[i]) return -1;
    if (a[i] > b[i]) return 1;
  }
  return a.length - b.length;
}

function minBy<T>(items: T[], key: (x: T) => Key): T {
  return items.reduce((best, x) => (compareKeys(key(x), key(best)) < 0 ? x : best));
}

export interface TripSummary {
  id: string;
  origin: string;
  destination: string;
  date: string;
  return_date: string | null;
  price: number;
  useful_minutes: number | null;
  destination_airport_change: boolean;
  needs_recheck: boolean;
}

export function summary(trip: Trip): TripSummary {
  const out = trip.outbound.segments;
  const back = trip.inbound;
  return {
    id: trip.id,
    origin: out[0].from_code,
    destination: out[out.length - 1].to_code,
    date: out[0].departure.slice(0, 10),
    return_date: back ? back.segments[0].departure.slice(0, 10) : null,
    price: trip.price,
    useful_minutes: trip.useful_minutes,
    destination_airport_change: trip.destination_airport_change ?? false,
    needs_recheck: trip.needs_recheck ?? false,
  };
}

export function facets(trips: Trip[]) {
  const carriers = new Map<string, string>();
  for (const t of trips) for (const s of segments(t)) carriers.set(s.airline_code, s.airline_name || s.airline_code);
  const byName = [...carriers.entries()].sort((a, b) => (a[1] < b[1] ? -1 : a[1] > b[1] ? 1 : 0));
  return {
    origins: [...new Set(trips.map((t) => t.outbound.segments[0].from_code))].sort(),
    destinations: [...new Set(trips.map((t) => t.outbound.segments[t.outbound.segments.length - 1].to_code))].sort(),
    airlines: byName.map(([c, n]) => ({ code: c, name: n })),
  };
}

export interface Recommendations {
  cheapest?: TripSummary;
  longest?: TripSummary;
  balanced?: TripSummary & { extra_price: number; extra_minutes: number };
}

export function recommendations(trips: Trip[]): Recommendations {
  if (!trips.length) return {};
  const cheapest = minBy(trips, (t) => sortKey(t, 'price'));
  const output: Recommendations = { cheapest: summary(cheapest) };
  const comparable = trips.filter((t) => t.inbound && t.useful_minutes && !t.destination_airport_change);
  if (!comparable.length) return output;
  output.longest = summary(minBy(comparable, (t) => sortKey(t, 'useful')));
  if (!comparable.includes(cheapest)) return output;
  const base = cheapest.useful_minutes as number;
  const allowance = Math.max(MAX_EXTRA_EUROS, cheapest.price * MAX_EXTRA_SHARE);
  const better = comparable.filter(
    (t) =>
      (t.useful_minutes as number) > base &&
      t.price <= cheapest.price + allowance &&
      t.price - cheapest.price <= (((t.useful_minutes as number) - base) / 60) * MAX_EUROS_PER_HOUR,
  );
  if (better.length) {
    const best = minBy(better, (t) => [t.price / (t.useful_minutes as number), t.price, t.id]);
    output.balanced = {
      ...summary(best),
      extra_price: round2(best.price - cheapest.price),
      extra_minutes: (best.useful_minutes as number) - base,
    };
  }
  return output;
}

export interface Group {
  city: string;
  destinations: string[];
  destination: string;
  count: number;
  cheapest: TripSummary;
  featured: TripSummary;
}

/** One entry per destination city: London groups STN, LTN, LGW and LHR. */
export function groups(trips: Trip[]): Group[] {
  const grouped = new Map<string, { destinations: Set<string>; count: number; cheapest: Trip; featured: TripSummary }>();
  for (const trip of trips) {
    const s = trip.outbound.segments;
    const destination = s[s.length - 1].to_code;
    const city = airportDetails(destination).city;
    if (!grouped.has(city)) {
      grouped.set(city, { destinations: new Set(), count: 0, cheapest: trip, featured: summary(trip) });
    }
    const group = grouped.get(city)!;
    group.destinations.add(destination);
    group.count += 1;
    if (compareKeys(sortKey(trip, 'price'), sortKey(group.cheapest, 'price')) < 0) group.cheapest = trip;
  }
  return [...grouped.entries()].map(([city, g]) => {
    const destinations = [...g.destinations].sort();
    return {
      city,
      destinations,
      destination: destinations.join(','),
      count: g.count,
      cheapest: summary(g.cheapest),
      featured: g.featured,
    };
  });
}

export function calendarPrices(trips: Trip[]) {
  const calendar = new Map<string, { date: string; return_date: string | null; price: number }>();
  for (const trip of trips) {
    const item = summary(trip);
    const key = `${item.date}|${item.return_date}`;
    const prev = calendar.get(key);
    if (!prev || item.price < prev.price) {
      calendar.set(key, { date: item.date, return_date: item.return_date, price: item.price });
    }
  }
  return [...calendar.values()].sort((a, b) =>
    compareKeys([a.date, a.return_date || ''], [b.date, b.return_date || '']),
  );
}

export function browseResults(trips: Trip[], sort: Sort | string = 'price', offset = 0, filters = new ResultFilters(), focus = '') {
  const filtered = trips.filter((t) => filters.matches(t)).sort((a, b) => compareKeys(sortKey(a, sort), sortKey(b, sort)));
  const focusedIndex = filtered.findIndex((t) => t.id === focus);
  let start = offset;
  if (focusedIndex >= 0) start = Math.floor(focusedIndex / PAGE_SIZE) * PAGE_SIZE;
  start = Math.min(start, Math.max(0, Math.floor((filtered.length - 1) / PAGE_SIZE) * PAGE_SIZE));
  return {
    result_count: trips.length,
    filtered_count: filtered.length,
    facets: facets(trips),
    groups: groups(filtered),
    recommendations: recommendations(filtered),
    calendar: calendarPrices(filtered),
    results: filtered.slice(start, start + PAGE_SIZE),
    offset: start,
    has_more: start + PAGE_SIZE < filtered.length,
    focused_id: focusedIndex >= 0 ? focus : null,
  };
}
