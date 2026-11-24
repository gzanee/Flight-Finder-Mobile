/**
 * Calendar planning and strict matching: port of flightcore/flexible.py.
 * Route lists are discovery seeds, never evidence of dated flight availability.
 * All windows are airport-local; elapsed times are measured in UTC.
 */
import { sha256 } from '@noble/hashes/sha256';
import { bytesToHex, utf8ToBytes } from '@noble/hashes/utils';
import { DESTINATION_SETS, airportCountry, exists } from './airports';
import {
  addDays,
  clockSeconds,
  daysBetween,
  hasOffset,
  instant,
  localClock,
  localDate,
  localInstant,
  parseDay,
  today as deviceToday,
  weekday,
} from './time';

export const MAX_ORIGINS = 10;
export const MAX_STOPS = 3;
export const MAX_DURATION_MINUTES = 4320;
export const MAX_PERIOD_DAYS = 366;
export const MAX_TRAVELERS = 9;
export const MAX_BUDGET = 100000;
export const MAX_DESTINATIONS = 200;
export const MAX_TASKS = 50000;
export const MAX_AIRPORTS_PER_SEARCH = 10;
export const GOOGLE_HORIZON_DAYS = 330;
export const SCHENGEN_COUNTRIES = new Set(
  'AT BE BG HR CZ DK EE FI FR DE GR HU IS IT LV LI LT LU MT NL NO PL PT RO SK SI ES SE CH'.split(' '),
);
export const LOW_COST = new Set('FR RK W4 W6 W9 U2 EC DS VY V7 HV TO PC EW'.split(' '));

export class SearchValidationError extends Error {
  name = 'SearchValidationError';
}

export type TripType = 'round_trip' | 'one_way';
export type DateMode = 'fixed' | 'flexible';
export type Seat = 'economy' | 'premium-economy' | 'business' | 'first';
export type Baggage = 'base' | 'cabin' | 'checked';

export interface FlexibleRequest {
  origins: string[];
  start_date: string;
  end_date: string;
  departure_days: number[];
  return_days: number[];
  departure_min: string;
  departure_max: string;
  return_min: string;
  return_max: string;
  min_nights: number;
  max_nights: number;
  max_stops: number;
  allow_self_transfer: boolean;
  return_airport: 'same' | 'group';
  budget: number;
  adults: number;
  children: number;
  airline_mode: 'all' | 'low_cost';
  baggage: Baggage;
  final_arrival_max: string | null;
  min_destination_hours: number;
  destinations: string[];
  currency: string;
  trip_type: TripType;
  destination_set: string;
  date_mode: DateMode;
  return_departure: 'same' | 'group';
  seat: Seat;
  infants: number;
  airlines: string[];
  exclude_basic_economy: boolean;
  allow_airport_change: boolean;
  schengen_transfers_only: boolean;
  departure_arrival_min: string;
  departure_arrival_max: string;
  return_arrival_min: string;
  return_arrival_max: string;
  min_layover_minutes: number;
  max_layover_minutes: number;
  departure_duration_min: number;
  departure_duration_max: number;
  return_duration_min: number;
  return_duration_max: number;
}

export interface Segment {
  from_code: string;
  to_code: string;
  departure: string;
  arrival: string;
  airline_code: string;
  airline_name: string;
  flight_number: string;
  duration_min: number;
}

export interface Quote {
  segments: Segment[];
  price: number;
  currency: string;
  duration_min: number;
  self_transfer: boolean | null;
  checked_at: string;
  verification: 'google_live';
  airline_verified: false;
  baggage: Baggage;
  baggage_basis: 'google_search_filter';
  travelers: number;
  seat: Seat;
  booking_url: string;
}

export interface Trip {
  id: string;
  trip_type: TripType;
  price: number;
  currency: string;
  outbound: Quote;
  inbound: Quote | null;
  useful_minutes: number | null;
  nights: number | null;
  ticketing: 'one_way' | 'separate_one_way';
  verification: 'google_live';
  airline_verified: false;
  checked_at: string;
  destination_airport_change?: boolean;
  // Added when results are presented.
  needs_recheck?: boolean;
  round_trip_from?: number;
  round_trip_url?: string | null;
}

type Data = Record<string, unknown>;

export function integer(value: unknown, label: string, low: number, high: number): number {
  if (typeof value === 'boolean' || value === null || value === undefined || !/^\d+$/.test(String(value))) {
    throw new SearchValidationError(`${label}: serve un numero intero.`);
  }
  const n = Number(value);
  if (!(low <= n && n <= high)) {
    throw new SearchValidationError(`${label}: scegli un valore fra ${low} e ${high}.`);
  }
  return n;
}

export function clock(value: unknown): string {
  if (typeof value !== 'string' || !/^\d{2}:\d{2}$/.test(value)) {
    throw new SearchValidationError('Gli orari devono avere formato HH:MM.');
  }
  if (Number(value.slice(0, 2)) > 23 || Number(value.slice(3)) > 59) {
    throw new SearchValidationError('Orario non valido.');
  }
  return value;
}

function weekdays(value: unknown, label: string): number[] {
  if (!Array.isArray(value) || !value.length) {
    throw new SearchValidationError(`Seleziona almeno un giorno per ${label}.`);
  }
  return [...new Set(value.map((v) => integer(v, label, 0, 6)))].sort((a, b) => a - b);
}

export function airportCodes(raw: unknown, label: string, maximum: number, required = false): string[] {
  let list = raw;
  if (typeof list === 'string') list = list.trim() ? list.trim().split(/[\s,;]+/) : [];
  if (!Array.isArray(list) || list.length > maximum || (required && !list.length)) {
    throw new SearchValidationError(`${label}: scegli da ${Number(required)} a ${maximum} aeroporti.`);
  }
  if (list.some((c) => typeof c !== 'string' || !exists(c.trim()))) {
    throw new SearchValidationError(`${label}: uno dei codici aeroporto non esiste.`);
  }
  return [...new Set((list as string[]).map((c) => c.trim().toUpperCase()))];
}

/** Python float(value): a number, or null where Python raises. */
export function pythonFloat(value: unknown): number | null {
  if (typeof value === 'number') return value;
  if (typeof value === 'boolean') return Number(value);
  if (typeof value !== 'string') return null;
  const s = value.trim().replace(/_/g, '');
  if (/^[+-]?(nan|inf|infinity)$/i.test(s)) return s.toLowerCase().includes('nan') ? Number.NaN : s.startsWith('-') ? -Infinity : Infinity;
  if (!/^[+-]?(\d+\.?\d*|\.\d+)(e[+-]?\d+)?$/i.test(s)) return null;
  return Number(s);
}

const get = (data: Data, key: string, fallback: unknown) => (key in data ? data[key] : fallback);

function choice<T extends string>(data: Data, key: string, allowed: readonly T[], fallback: T): T {
  const value = get(data, key, fallback) as T;
  if (!allowed.includes(value)) throw new SearchValidationError(`Valore non valido per ${key}.`);
  return value;
}

/** FlexibleRequest.parse: validate the request at one boundary. */
export function parseRequest(input: unknown, today = deviceToday()): FlexibleRequest {
  if (!input || typeof input !== 'object' || Array.isArray(input)) {
    throw new SearchValidationError('Invia un oggetto JSON.');
  }
  const data = input as Data;
  const fixedOneWay = data.date_mode === 'fixed' && data.trip_type === 'one_way';
  let start: string;
  let end: string;
  try {
    start = String(data.start_date);
    end = String(fixedOneWay ? data.start_date : data.end_date);
    parseDay(start);
    parseDay(end);
  } catch {
    throw new SearchValidationError('Indica inizio e fine del periodo di ricerca.');
  }
  if (start < today || end < start || daysBetween(start, end) > MAX_PERIOD_DAYS) {
    throw new SearchValidationError('Periodo non valido: date future, massimo un anno.');
  }
  const v: Data = {
    origins: airportCodes(get(data, 'origins', ['VCE', 'TSF']), 'Partenza', MAX_ORIGINS, true),
    start_date: start,
    end_date: end,
  };
  v.trip_type = choice(data, 'trip_type', ['round_trip', 'one_way'], 'round_trip');
  v.date_mode = choice(data, 'date_mode', ['fixed', 'flexible'], 'flexible');
  if (v.trip_type === 'one_way' && v.date_mode === 'fixed') v.end_date = v.start_date;
  for (const [key, fallback] of [
    ['departure_days', [4]],
    ['return_days', [6]],
  ] as const) {
    const ignored = v.date_mode === 'fixed' || (key === 'return_days' && v.trip_type === 'one_way');
    v[key] = ignored ? [0, 1, 2, 3, 4, 5, 6] : weekdays(get(data, key, fallback), key);
  }
  for (const prefix of ['departure', 'return', 'departure_arrival', 'return_arrival']) {
    for (const [suffix, fallback] of [
      ['min', '18:00'],
      ['max', '23:59'],
    ]) {
      const key = `${prefix}_${suffix}`;
      const def = prefix.endsWith('arrival') && suffix === 'min' ? '00:00' : fallback;
      v[key] = get(data, key, def);
      clock(v[key]);
    }
    if ((v[`${prefix}_min`] as string) > (v[`${prefix}_max`] as string)) {
      throw new SearchValidationError('La fascia oraria deve stare nello stesso giorno.');
    }
  }
  for (const [key, fallback, low, high] of [
    ['min_nights', 2, 0, 30],
    ['max_nights', 2, 0, 30],
    ['max_stops', 0, 0, MAX_STOPS],
    ['adults', 1, 1, 9],
    ['children', 0, 0, 8],
    ['infants', 0, 0, 8],
    ['min_destination_hours', 0, 0, 720],
  ] as const) {
    const ignored =
      (key === 'min_nights' || key === 'max_nights') && (v.date_mode === 'fixed' || v.trip_type === 'one_way');
    v[key] = ignored ? fallback : integer(get(data, key, fallback), key, low, high);
  }
  if ((v.min_nights as number) > (v.max_nights as number)) {
    throw new SearchValidationError('Il minimo di notti supera il massimo.');
  }
  if ((v.adults as number) + (v.children as number) + (v.infants as number) > MAX_TRAVELERS) {
    throw new SearchValidationError('Massimo 9 viaggiatori.');
  }
  if ((v.infants as number) > (v.adults as number)) {
    throw new SearchValidationError('Serve un adulto per ogni neonato in braccio.');
  }
  const budget = pythonFloat(get(data, 'budget', 100));
  if (budget === null) throw new SearchValidationError('Budget non valido.');
  if (typeof data.budget === 'boolean' || !Number.isFinite(budget) || !(budget > 0 && budget <= MAX_BUDGET)) {
    throw new SearchValidationError('Il budget totale deve essere positivo e finito.');
  }
  v.budget = budget;
  v.return_airport = choice(data, 'return_airport', ['same', 'group'], 'same');
  v.airline_mode = choice(data, 'airline_mode', ['all', 'low_cost'], 'all');
  v.baggage = choice(data, 'baggage', ['base', 'cabin', 'checked'], 'base');
  v.return_departure = choice(data, 'return_departure', ['same', 'group'], 'same');
  v.seat = choice(data, 'seat', ['economy', 'premium-economy', 'business', 'first'], 'economy');
  for (const key of ['allow_self_transfer', 'allow_airport_change', 'exclude_basic_economy', 'schengen_transfers_only']) {
    v[key] = get(data, key, false);
    if (typeof v[key] !== 'boolean') throw new SearchValidationError(`${key} deve essere vero o falso.`);
  }
  for (const prefix of ['departure_duration', 'return_duration']) {
    v[`${prefix}_min`] = integer(get(data, `${prefix}_min`, 0), prefix, 0, MAX_DURATION_MINUTES);
    v[`${prefix}_max`] = integer(get(data, `${prefix}_max`, MAX_DURATION_MINUTES), prefix, 0, MAX_DURATION_MINUTES);
    if ((v[`${prefix}_min`] as number) > (v[`${prefix}_max`] as number)) {
      throw new SearchValidationError('La durata minima supera quella massima.');
    }
  }
  for (const [bound, fallback] of [
    ['min', 0],
    ['max', MAX_DURATION_MINUTES],
  ] as const) {
    const key = `${bound}_layover_minutes`;
    v[key] = integer(get(data, key, fallback), 'Durata scalo', 0, MAX_DURATION_MINUTES);
  }
  if ((v.min_layover_minutes as number) > (v.max_layover_minutes as number)) {
    throw new SearchValidationError('Lo scalo minimo supera quello massimo.');
  }
  let airlines = get(data, 'airlines', []);
  if (typeof airlines === 'string') airlines = airlines.trim() ? airlines.trim().split(/[\s,;]+/) : [];
  if (
    !Array.isArray(airlines) ||
    airlines.some((c) => typeof c !== 'string' || !/^[A-Z0-9]{2}$/.test(c.toUpperCase()))
  ) {
    throw new SearchValidationError('Compagnie: usa codici IATA di due caratteri, es. FR, V7.');
  }
  v.airlines = [...new Set((airlines as string[]).map((c) => c.toUpperCase()))];
  const final = (data.final_arrival_max as string) || null;
  if (final) clock(final);
  v.final_arrival_max = final;
  v.destinations = airportCodes(get(data, 'destinations', []), 'Destinazioni', MAX_DESTINATIONS);
  v.destination_set = get(data, 'destination_set', 'real');
  if (v.destination_set !== 'real' && !(String(v.destination_set) in DESTINATION_SETS)) {
    throw new SearchValidationError('Gruppo di destinazioni non riconosciuto.');
  }
  if (v.return_departure === 'group' && !(v.destinations as string[]).length) {
    throw new SearchValidationError(
      'Per ripartire da un altro aeroporto scegli un gruppo di destinazioni esplicito.',
    );
  }
  v.currency = 'EUR';
  const request = v as unknown as FlexibleRequest;
  if (!datePairs(request).length) {
    throw new SearchValidationError('Nessuna coppia di date rispetta giorni e notti scelti.');
  }
  return request;
}

export type DatePair = [string, string | null];

export function datePairs(r: FlexibleRequest): DatePair[] {
  if (r.date_mode === 'fixed') return [[r.start_date, r.trip_type === 'round_trip' ? r.end_date : null]];
  const pairs: DatePair[] = [];
  for (let day = r.start_date; day <= r.end_date; day = addDays(day, 1)) {
    if (!r.departure_days.includes(weekday(day))) continue;
    if (r.trip_type === 'one_way') {
      pairs.push([day, null]);
      continue;
    }
    for (let nights = r.min_nights; nights <= r.max_nights; nights++) {
      const back = addDays(day, nights);
      if (back <= r.end_date && r.return_days.includes(weekday(back))) pairs.push([day, back]);
    }
  }
  return pairs;
}

/** json.dumps of the segment identities, hashed: the same ids as the Python engine. */
export function fingerprint(quote: Pick<Quote, 'segments'>): string {
  const parts = quote.segments.map(
    (s) =>
      `[${[s.from_code, s.to_code, s.departure, s.arrival, s.airline_code, s.flight_number]
        .map((x) => JSON.stringify(x))
        .join(', ')}]`,
  );
  return bytesToHex(sha256(utf8ToBytes(`[${parts.join(', ')}]`))).slice(0, 24);
}

function pairwise<T>(items: T[]): [T, T][] {
  return items.slice(1).map((b, i) => [items[i], b]);
}

/** Validate every constraint again against returned facts, down to the minute. */
export function matches(
  quote: Quote,
  r: FlexibleRequest,
  origin: string,
  destination: string,
  day: string,
  inbound = false,
): boolean {
  if (quote.verification !== 'google_live' || !quote.segments?.length) return false;
  const segments = quote.segments;
  if (segments[0].from_code !== origin || segments[segments.length - 1].to_code !== destination) return false;
  if (segments.length - 1 > r.max_stops) return false;
  if (!r.allow_self_transfer && segments.length > 1 && quote.self_transfer === true) return false;
  const links = pairwise(segments);
  if (!r.allow_airport_change && links.some(([a, b]) => a.to_code !== b.from_code)) return false;
  if (
    r.schengen_transfers_only &&
    links.some(([a, b]) =>
      [a.to_code, b.from_code].some((code) => !SCHENGEN_COUNTRIES.has(airportCountry(code) ?? '')),
    )
  ) {
    return false;
  }
  if (quote.currency !== r.currency || quote.baggage !== r.baggage) return false;
  const price = quote.price;
  if (typeof price !== 'number' || !Number.isFinite(price) || price <= 0) return false;
  if (quote.travelers !== r.adults + r.children + r.infants) return false;
  let previous: string | null = null;
  for (const seg of segments) {
    if (!seg.flight_number || !seg.airline_code) return false;
    if (r.airline_mode === 'low_cost' && !LOW_COST.has(seg.airline_code)) return false;
    if (r.airlines.length && !r.airlines.includes(seg.airline_code)) return false;
    if (!hasOffset(seg.departure) || !hasOffset(seg.arrival)) return false;
    const dep = instant(seg.departure);
    const arr = instant(seg.arrival);
    if (arr <= dep || (previous && dep < instant(previous))) return false;
    if (previous) {
      const layover = (dep - instant(previous)) / 60000;
      if (!(r.min_layover_minutes <= layover && layover <= r.max_layover_minutes)) return false;
    }
    previous = seg.arrival;
  }
  const departure = segments[0].departure;
  if (localDate(departure) !== day) return false;
  const prefix = inbound ? 'return' : 'departure';
  const depClock = localClock(departure);
  if (!(clockSeconds(r[`${prefix}_min`]) <= depClock && depClock <= clockSeconds(r[`${prefix}_max`]))) {
    return false;
  }
  const arrival = segments[segments.length - 1].arrival;
  const elapsed = (instant(arrival) - instant(departure)) / 60000;
  if (!(r[`${prefix}_duration_min`] <= elapsed && elapsed <= r[`${prefix}_duration_max`])) return false;
  const arrClock = localClock(arrival);
  if (
    !(clockSeconds(r[`${prefix}_arrival_min`]) <= arrClock && arrClock <= clockSeconds(r[`${prefix}_arrival_max`]))
  ) {
    return false;
  }
  if ((inbound || r.trip_type === 'one_way') && r.final_arrival_max) {
    const deadline = localInstant(`${day}T${r.final_arrival_max}:00`, destination);
    if (instant(arrival) > instant(deadline)) return false;
  }
  return true;
}

export function singleTrip(outbound: Quote, r: FlexibleRequest): Trip | null {
  if (outbound.price > r.budget) return null;
  return {
    id: fingerprint(outbound),
    trip_type: 'one_way',
    price: outbound.price,
    currency: r.currency,
    outbound,
    inbound: null,
    useful_minutes: null,
    nights: null,
    ticketing: 'one_way',
    verification: 'google_live',
    airline_verified: false,
    checked_at: outbound.checked_at,
  };
}

/** Python round(x, 2) for prices (banker's rounding on exact halves is irrelevant at cents). */
export function round2(x: number): number {
  return Math.round(x * 100) / 100;
}

/** Two live one-way prices, never an estimated round-trip lead price. */
export function combine(outbound: Quote, inbound: Quote, r: FlexibleRequest): Trip | null {
  const total = round2(outbound.price + inbound.price);
  if (total > r.budget) return null;
  const arrival = outbound.segments[outbound.segments.length - 1].arrival;
  const departure = inbound.segments[0].departure;
  const useful = Math.trunc((instant(departure) - instant(arrival)) / 60000);
  if (useful <= 0 || useful < r.min_destination_hours * 60) return null;
  return {
    trip_type: 'round_trip',
    id: fingerprint(outbound) + fingerprint(inbound),
    price: total,
    currency: r.currency,
    outbound,
    inbound,
    useful_minutes: useful,
    nights: daysBetween(localDate(outbound.segments[0].departure), localDate(departure)),
    ticketing: 'separate_one_way',
    verification: 'google_live',
    airline_verified: false,
    checked_at: outbound.checked_at < inbound.checked_at ? outbound.checked_at : inbound.checked_at,
    destination_airport_change: outbound.segments[outbound.segments.length - 1].to_code !== inbound.segments[0].from_code,
  };
}

export type Route = [string, string];

export function returnRoutes(r: FlexibleRequest, origin: string, destination: string): Route[] {
  if (r.trip_type === 'one_way') return [];
  const departures = r.return_departure === 'group' ? r.destinations : [destination];
  const homes = r.return_airport === 'group' ? r.origins : [origin];
  return departures.flatMap((away) => homes.filter((home) => home !== away).map((home): Route => [away, home]));
}

export function airportsPerSearch(r: Pick<FlexibleRequest, 'max_stops'>): number {
  return r.max_stops === 0 ? MAX_AIRPORTS_PER_SEARCH : 1;
}

export type Batch = [string[], string[]];

/** Cover exactly the (from, to) pairs with searches of up to `size` airports per side. */
export function pairBatches(input: Route[], size: number): Batch[] {
  const seen = new Set<string>();
  const pairs: Route[] = [];
  for (const [a, b] of input) {
    const key = `${a}|${b}`;
    if (!seen.has(key)) {
      seen.add(key);
      pairs.push([a, b]);
    }
  }
  if (size <= 1) return pairs.map(([a, b]) => [[a], [b]]);
  const grouped = (swap: boolean): Batch[] => {
    const partners = new Map<string, string[]>();
    for (const [a, b] of pairs) {
      const [key, other] = swap ? [b, a] : [a, b];
      if (!partners.has(key)) partners.set(key, []);
      partners.get(key)!.push(other);
    }
    const groups = new Map<string, { others: string[]; keys: string[] }>();
    for (const [key, others] of partners) {
      const id = others.join('|');
      if (!groups.has(id)) groups.set(id, { others, keys: [] });
      groups.get(id)!.keys.push(key);
    }
    const batches: Batch[] = [];
    for (const { others, keys } of groups.values()) {
      for (let i = 0; i < keys.length; i += size) {
        for (let j = 0; j < others.length; j += size) {
          const part: Batch = [keys.slice(i, i + size), others.slice(j, j + size)];
          batches.push(swap ? [part[1], part[0]] : part);
        }
      }
    }
    return batches;
  };
  const plain = grouped(false);
  const swapped = grouped(true);
  return swapped.length < plain.length ? swapped : plain;
}

/** Day-by-day work for one request: each flight leg is searched once (see Schedule in Python). */
export class Schedule {
  request: FlexibleRequest;
  size: number;
  horizon: string;
  datePairs: DatePair[];
  unsoldFrom: string | null;
  backDays: string[];
  days: string[];
  outDays: Set<string>;
  outsFor: Map<string, string[]>;
  lastBack: Map<string, string>;
  routes: Route[];
  outBatches: Batch[];
  returns: Route[];
  backBound: number;
  total: number;

  constructor(request: FlexibleRequest, destinations: string[], today = deviceToday()) {
    this.request = request;
    this.size = airportsPerSearch(request);
    this.horizon = addDays(today, GOOGLE_HORIZON_DAYS);
    const pairs = datePairs(request);
    this.datePairs = pairs.filter(([out, back]) => (back || out) <= this.horizon);
    const unsold = pairs.filter((p) => !this.datePairs.includes(p)).map(([out, back]) => back || out);
    this.unsoldFrom = unsold.length ? unsold.sort()[0] : null;
    const outDays = [...new Set(this.datePairs.map(([out]) => out))].sort();
    this.backDays = [...new Set(this.datePairs.map(([, back]) => back).filter((b): b is string => Boolean(b)))].sort();
    this.days = [...new Set([...outDays, ...this.backDays])].sort();
    this.outDays = new Set(outDays);
    this.outsFor = new Map();
    for (const [out, back] of this.datePairs) {
      if (back) {
        if (!this.outsFor.has(back)) this.outsFor.set(back, []);
        this.outsFor.get(back)!.push(out);
      }
    }
    this.lastBack = new Map();
    for (const [out, back] of this.datePairs) {
      const prev = this.lastBack.get(out) ?? '';
      const b = back ?? '';
      this.lastBack.set(out, prev > b ? prev : b);
    }
    this.routes = destinations.flatMap((d) => request.origins.filter((o) => o !== d).map((o): Route => [o, d]));
    this.outBatches = pairBatches(this.routes, this.size);
    const seen = new Set<string>();
    this.returns = [];
    for (const [o, d] of this.routes) {
      for (const route of returnRoutes(request, o, d)) {
        const key = route.join('|');
        if (!seen.has(key)) {
          seen.add(key);
          this.returns.push(route);
        }
      }
    }
    this.backBound = pairBatches(this.returns, this.size).length;
    this.total = outDays.length * this.outBatches.length + this.backDays.length * this.backBound;
  }

  returnBatches(wanted: Route[]): Batch[] {
    if (this.size === 1) return pairBatches(wanted, 1);
    const aways = new Set(wanted.map(([away]) => away));
    return pairBatches(
      this.returns.filter(([away]) => aways.has(away)),
      this.size,
    );
  }

  remaining(cursor: [number, number, number], returnShare = 1): number {
    const [index, stage, batch] = cursor;
    let outs = 0;
    let returns = 0;
    for (let i = index; i < this.days.length; i++) {
      const day = this.days[i];
      const current = i === index;
      if (this.outDays.has(day) && !(current && stage)) outs += this.outBatches.length - (current ? batch : 0);
      returns += this.backBound * Number(this.outsFor.has(day));
    }
    return outs + returns * returnShare;
  }

  unitsBefore(dayIndex: number): number {
    let units = 0;
    for (const day of this.days.slice(0, dayIndex)) {
      units +=
        this.outBatches.length * Number(this.outDays.has(day)) + this.backBound * Number(this.outsFor.has(day));
    }
    return units;
  }
}

export function searchPasses(request: FlexibleRequest, destinations: string[], today = deviceToday()): Schedule[] {
  const full = new Schedule(request, destinations, today);
  if (request.max_stops === 0 || full.routes.length <= MAX_AIRPORTS_PER_SEARCH) return [full];
  return [new Schedule({ ...request, max_stops: 0 }, destinations, today), full];
}

export interface Plan {
  date_pairs: DatePair[];
  destinations: string[];
  candidate_count: number;
  combinations: number;
  distinct_searches: number;
  searches_upper_bound: number;
  airports_per_search: number;
  direct_first: boolean;
  unsold_from: string | null;
  coverage: string;
  origin_route_counts: Record<string, number>;
  notice: string;
}

const thousands = (n: number) => n.toLocaleString('en-US');

/** Present work and coverage before starting; never truncate silently. */
export function buildPlan(r: FlexibleRequest, routeMap: Record<string, string[]>, today = deviceToday()): Plan {
  let candidates: string[];
  let source: string;
  if (r.destinations.length) {
    candidates = [...r.destinations];
    source = 'Destinazioni indicate da te';
  } else {
    candidates = [...new Set(Object.values(routeMap).flat())].sort();
    source = 'Destinazioni candidate dai cataloghi delle rotte; copertura non esaustiva';
    if (r.destination_set !== 'real') source = `Gruppo di destinazioni: ${DESTINATION_SETS[r.destination_set].label}`;
  }
  candidates = candidates.filter((c) => r.origins.some((o) => c !== o));
  if (!candidates.length) throw new SearchValidationError('Nessuna destinazione candidata disponibile.');
  const pairs = datePairs(r);
  const taskCount = pairs.length * candidates.reduce((n, c) => n + r.origins.filter((o) => o !== c).length, 0);
  if (taskCount > MAX_TASKS) {
    throw new SearchValidationError(
      `La ricerca genera ${thousands(taskCount)} combinazioni: massimo ${thousands(MAX_TASKS)}. ` +
        "Riduci il periodo, le destinazioni o l'intervallo di notti.",
    );
  }
  const passes = searchPasses(r, candidates, today);
  const schedule = passes[passes.length - 1];
  let legs = 0;
  for (const [, back] of pairs) {
    for (const [origin, dest] of schedule.routes) {
      legs += 1 + (back ? returnRoutes(r, origin, dest).length : 0);
      if (legs > MAX_TASKS * 2) {
        throw new SearchValidationError('Riduci gli aeroporti o le date: troppi controlli di ritorno.');
      }
    }
  }
  const returns = new Set(schedule.routes.flatMap(([o, d]) => returnRoutes(r, o, d).map((x) => x.join('|'))));
  return {
    date_pairs: pairs,
    destinations: candidates,
    candidate_count: candidates.length,
    combinations: taskCount,
    distinct_searches: schedule.outDays.size * schedule.routes.length + schedule.backDays.length * returns.size,
    searches_upper_bound: passes.reduce((n, p) => n + p.total, 0),
    airports_per_search: passes[0].size,
    direct_first: passes.length > 1,
    unsold_from: schedule.unsoldFrom,
    coverage: source,
    origin_route_counts: Object.fromEntries(r.origins.map((o) => [o, (routeMap[o] ?? []).length])),
    notice: 'Gli orari da catalogo non confermano un volo. Solo offerte live complete nei risultati.',
  };
}
