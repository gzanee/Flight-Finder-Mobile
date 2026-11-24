/**
 * Google Flights page -> live quotes: port of live_quotes.decode() and of the
 * parts of fli's parse_flight_row (and its pydantic validation) that decide
 * whether a row is accepted. A schema mismatch is an error, never an empty route.
 */
import airlineCodes from './data/airlines.json';
import { knownToDecoder } from './airports';
import { type FlexibleRequest, type Quote, fingerprint } from './flexible';
import { localInstant, utcNow } from './time';

export class LiveUnavailable extends Error {
  name = 'LiveUnavailable';
}
export class LiveBlocked extends LiveUnavailable {
  name = 'LiveBlocked';
}
export class LiveTransient extends LiveUnavailable {
  name = 'LiveTransient';
}

class RowError extends Error {}

const AIRLINES = new Set(airlineCodes as string[]);
const AIRLINE_SENTINELS = new Set(['multi']);

type Json = unknown;
const isList = (v: Json): v is Json[] => Array.isArray(v);
const safeGet = (seq: Json, i: number): Json => (isList(seq) && i >= 0 && i < seq.length ? seq[i] : null);
const isInt = (v: Json): v is number => typeof v === 'number' && Number.isInteger(v);
const asStr = (v: Json): string | null => (typeof v === 'string' && v ? v : null);
const asBool = (v: Json): boolean | null => (typeof v === 'boolean' ? v : null);

/** Python indexing: negative indexes count from the end, out of range raises. */
function at(seq: Json, i: number): Json {
  if (!isList(seq)) throw new RowError('not a list');
  const index = i < 0 ? seq.length + i : i;
  if (index < 0 || index >= seq.length) throw new RowError('index');
  return seq[index];
}

/** fli's Airline enum member name: digits-first codes are prefixed with "_". */
function airlineName(code: Json): string | null {
  if (typeof code !== 'string' || !code || AIRLINE_SENTINELS.has(code)) return null;
  return AIRLINES.has(code) ? (/^\d/.test(code) ? `_${code}` : code) : null;
}

const B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';

/** base64.urlsafe_b64decode, lenient like Python's non-validating decoder. */
export function b64decode(token: string): number[] {
  const values: number[] = [];
  for (const ch of token.replace(/-/g, '+').replace(/_/g, '/')) {
    if (ch === '=') break;
    const v = B64.indexOf(ch);
    if (v >= 0) values.push(v);
  }
  if (values.length % 4 === 1) throw new Error('Incorrect padding');
  const out: number[] = [];
  for (let i = 0; i < values.length; i += 4) {
    const chunk = values.slice(i, i + 4);
    const n = chunk.reduce((acc, v, k) => acc | (v << (18 - 6 * k)), 0);
    out.push((n >> 16) & 255);
    if (chunk.length > 2) out.push((n >> 8) & 255);
    if (chunk.length > 3) out.push(n & 255);
  }
  return out;
}

function readVarint(data: number[], offset: number): [number, number] {
  let result = 0;
  let shift = 0;
  let pos = offset;
  for (;;) {
    if (pos >= data.length) throw new Error('Truncated varint');
    const byte = data[pos++];
    result += (byte & 0x7f) * 2 ** shift;
    if (!(byte & 0x80)) return [result, pos];
    shift += 7;
    if (shift > 63) throw new Error('Varint too long');
  }
}

function readDelimited(data: number[], offset: number): [number[], number] {
  const [length, start] = readVarint(data, offset);
  const end = start + length;
  if (end > data.length) throw new Error('Length-delimited field exceeds payload size');
  return [data.slice(start, end), end];
}

function skipField(data: number[], offset: number, wire: number): number {
  if (wire === 0) return readVarint(data, offset)[1];
  if (wire === 1 || wire === 5) {
    const end = offset + (wire === 1 ? 8 : 4);
    if (end > data.length) throw new Error('Fixed field exceeds payload size');
    return end;
  }
  if (wire === 2) return readDelimited(data, offset)[1];
  throw new Error(`Unsupported wire type: ${wire}`);
}

function utf8Decode(bytes: number[]): string {
  return decodeURIComponent(bytes.map((b) => `%${b.toString(16).padStart(2, '0')}`).join(''));
}

/** fli.core.extract_currency_from_price_token. */
export function currencyFromToken(token: Json): string | null {
  if (typeof token !== 'string' || !token) return null;
  try {
    const data = b64decode(token);
    let offset = 0;
    while (offset < data.length) {
      const [tag, next] = readVarint(data, offset);
      offset = next;
      const field = Math.floor(tag / 8);
      const wire = tag & 7;
      if (field === 3 && wire === 2) {
        const [nested, after] = readDelimited(data, offset);
        offset = after;
        let pos = 0;
        while (pos < nested.length) {
          const [ntag, npos] = readVarint(nested, pos);
          pos = npos;
          if (Math.floor(ntag / 8) === 3 && (ntag & 7) === 2) {
            return utf8Decode(readDelimited(nested, pos)[0]).toUpperCase();
          }
          pos = skipField(nested, pos, ntag & 7);
        }
        continue;
      }
      offset = skipField(data, offset, wire);
    }
    return null;
  } catch {
    return null;
  }
}

interface Leg {
  airline: string;
  flightNumber: string;
  from: string;
  to: string;
  departure: string; // naive local "YYYY-MM-DDTHH:MM:SS"
  arrival: string;
  duration: number;
}

/** Python datetime(*date, *time) from Google's [y,m,d] + [h,m] arrays. */
function naiveDatetime(dateArr: Json, timeArr: Json): string {
  if (!isList(dateArr) || !isList(timeArr)) throw new RowError('date/time not a list');
  if (!dateArr.some((x) => x !== null) || !timeArr.some((x) => x !== null)) throw new RowError('empty date');
  const parts = [...dateArr, ...timeArr].map((x) => x || 0);
  if (parts.length < 3 || parts.length > 8 || parts.some((x) => !isInt(x))) throw new RowError('bad datetime');
  const [y, mo, d, h = 0, mi = 0, s = 0] = parts as number[];
  const ms = Date.UTC(y, mo - 1, d, h, mi, s);
  const dt = new Date(ms);
  if (
    y < 1 ||
    y > 9999 ||
    dt.getUTCFullYear() !== y ||
    dt.getUTCMonth() !== mo - 1 ||
    dt.getUTCDate() !== d ||
    h > 23 ||
    mi > 59 ||
    s > 59 ||
    h < 0 ||
    mi < 0 ||
    s < 0
  ) {
    throw new RowError('datetime out of range');
  }
  const p = (n: number, w = 2) => String(n).padStart(w, '0');
  return `${p(y, 4)}-${p(mo)}-${p(d)}T${p(h)}:${p(mi)}:${p(s)}`;
}

/** PositiveInt in pydantic's lax mode: ints, integral floats, digit strings. */
function positiveInt(v: Json): number {
  let n: number | null = null;
  if (typeof v === 'boolean') n = Number(v);
  else if (typeof v === 'number' && Number.isInteger(v)) n = v;
  else if (typeof v === 'string' && /^\s*[+-]?\d+\s*$/.test(v)) n = Number(v);
  if (n === null || n <= 0) throw new RowError('not a positive int');
  return n;
}

function parseLeg(fl: Json): Leg {
  const airlineInfo = at(fl, 22) || [];
  const airline = airlineName(safeGet(airlineInfo, 0));
  if (airline === null) throw new RowError('unknown airline'); // FlightLeg.airline is required
  const flightNumber = asStr(safeGet(airlineInfo, 1)) ?? '';
  const from = at(fl, 3);
  const to = at(fl, 6);
  if (typeof from !== 'string' || !knownToDecoder(from)) throw new RowError('unknown airport');
  if (typeof to !== 'string' || !knownToDecoder(to)) throw new RowError('unknown airport');
  return {
    airline,
    flightNumber,
    from,
    to,
    departure: naiveDatetime(at(fl, 20), at(fl, 8)),
    arrival: naiveDatetime(at(fl, 21), at(fl, 10)),
    duration: positiveInt(at(fl, 11)),
  };
}

function parsePrice(row: Json): [number | null, string | null] {
  const block = safeGet(row, 1);
  if (!isList(block)) throw new RowError('price block missing');
  const head = at(block, 0);
  if (!isList(head)) throw new RowError('price head is not a list');
  let price: number | null = null;
  if (head.length) {
    const raw = head[head.length - 1];
    if (typeof raw !== 'number') throw new RowError('price field is not numeric');
    price = raw;
    if (price < 0) throw new RowError('negative price'); // NonNegativeFloat
  }
  const currency = block.length > 1 ? currencyFromToken(block[1]) : null;
  return [price, currency];
}

interface FlightRow {
  price: number | null;
  currency: string | null;
  duration: number;
  legs: Leg[];
  selfTransfer: boolean | null;
  bookingToken: string | null;
}

/** fli.parse_flight_row, keeping only what flightcore reads. */
export function parseFlightRow(row: Json): FlightRow {
  const detail = at(row, 0);
  const [price, currency] = parsePrice(row);
  const rawLegs = at(detail, 2) || [];
  if (!isList(rawLegs)) throw new RowError('legs not a list');
  const legs = rawLegs.map(parseLeg);
  return {
    price,
    currency,
    duration: positiveInt(at(detail, 9)),
    legs,
    selfTransfer: asBool(safeGet(detail, 12)),
    bookingToken: asStr(safeGet(row, 8)),
  };
}

function shoppingScript(html: string): string | null {
  const re = /<script\b([^>]*)>([\s\S]*?)<\/script\s*>/gi;
  for (let m = re.exec(html); m; m = re.exec(html)) {
    const cls = /\bclass\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))/i.exec(m[1]);
    const classes = (cls?.[1] ?? cls?.[2] ?? cls?.[3] ?? '').split(/\s+/);
    if (classes.includes('ds:1')) return m[2];
  }
  return null;
}

const IATA = /^[A-Z0-9]{3}$/;

function sameSet(a: string[], b: string[]) {
  const sa = new Set(a);
  const sb = new Set(b);
  return sa.size === sb.size && [...sa].every((x) => sb.has(x));
}

export type ExpectedRoute = [readonly string[], readonly string[]] | null;

/** Null result groups count as "no flights" only inside the observed envelope. */
function isEmptyShoppingResponse(payload: Json, expected: ExpectedRoute): boolean {
  try {
    const header = at(payload, 0);
    const locations = at(payload, 1);
    const filters = at(payload, 7);
    const context = at(payload, 12);
    const first = at(locations, 0);
    if (!isList(first) || first.length !== 2) return false;
    const [departure, arrival] = first;
    if (!isList(departure) || !isList(arrival)) return false;
    const code = (airport: Json) => at(at(airport, 0), 0);
    const origins = departure.map(code);
    const destinations = arrival.map(code);
    return (
      isList(locations) &&
      locations.length === 1 &&
      origins.length > 0 &&
      destinations.length > 0 &&
      [...origins, ...destinations].every((c) => typeof c === 'string' && IATA.test(c)) &&
      (expected === null ||
        (sameSet(origins as string[], [...expected[0]]) && sameSet(destinations as string[], [...expected[1]]))) &&
      isList(header) &&
      isInt(at(header, 2)) &&
      at(header, 2) === 0 &&
      isList(at(header, 1)) &&
      (at(header, 1) as Json[]).length > 0 &&
      isList(filters) &&
      filters.length > 0 &&
      isList(context) &&
      context.length === 2 &&
      typeof context[0] === 'string' &&
      context[0].length > 0 &&
      isInt(context[1]) &&
      context[1] === 0 &&
      at(payload, 23) === 1
    );
  } catch {
    return false;
  }
}

export type DecodeRequest = Pick<
  FlexibleRequest,
  'currency' | 'baggage' | 'adults' | 'children' | 'infants' | 'seat'
>;

/** Quotes on the page and the number of rows that could not be trusted. */
export function decode(
  html: string,
  request: DecodeRequest,
  url: string,
  expected: ExpectedRoute = null,
): [Quote[], number] {
  const script = shoppingScript(html);
  if (script === null) {
    throw new LiveUnavailable('Pagina Google senza dati voli: consenso, blocco o formato cambiato.');
  }
  if (script.includes('travel.frontend.flights.ErrorResponse')) {
    throw new LiveTransient(
      'Google ha restituito un errore temporaneo anche dopo vari tentativi. Puoi premere Riprendi.',
    );
  }
  let payload: Json;
  const rows: Json[] = [];
  try {
    const after = script.indexOf('data:');
    if (after < 0) throw new Error('no data');
    const rest = script.slice(after + 5);
    payload = JSON.parse(rest.slice(0, rest.lastIndexOf(',') < 0 ? rest.length : rest.lastIndexOf(',')));
    let found = false;
    for (const slot of [2, 3]) {
      const block = at(payload, slot);
      if (block === null) continue;
      if (isList(block) && block.length && (block[0] === null || isList(block[0]))) {
        found = true;
        rows.push(...((block[0] as Json[]) || []));
      } else {
        throw new Error('Malformed shopping group');
      }
    }
    if (!found && !isEmptyShoppingResponse(payload, expected)) throw new Error('No shopping rows');
  } catch {
    throw new LiveUnavailable('Risposta Google non riconosciuta: non equivale a zero voli.');
  }
  // IATA codes can be reassigned: use the names of this live response.
  let names: Record<string, string> = {};
  try {
    const list = at(at(at(payload, 7), 1), 1);
    if (!isList(list)) throw new RowError('names');
    for (const item of list) names[String(at(item, 0))] = at(item, 1) as string;
  } catch {
    names = {};
  }
  const quotes: Quote[] = [];
  const seen = new Set<string>();
  let rejected = 0;
  const checked = utcNow();
  for (const raw of rows) {
    try {
      const row = structuredCloneJson(raw);
      // Google omits zero minute components; normalize before decoding.
      const segs = at(at(row, 0), 2);
      if (!isList(segs)) throw new RowError('segments');
      for (const seg of segs) {
        for (const slot of [8, 10]) {
          const t = at(seg, slot);
          if (!isList(t)) throw new RowError('Missing local time');
          (seg as Json[])[slot] = [...t, 0, 0].slice(0, 2).map((x) => x || 0);
        }
      }
      const result = parseFlightRow(row);
      if (result.price === null || result.price <= 0 || result.currency !== request.currency) {
        throw new RowError('Missing live price or currency');
      }
      if (!result.bookingToken || !result.legs.length) throw new RowError('Missing shopping evidence');
      const rawNames = at(at(raw, 0), 1);
      const singleName = isList(rawNames) && rawNames.length === 1 ? rawNames[0] : null;
      const segments = result.legs.map((leg) => {
        if (!leg.flightNumber || !leg.airline) throw new RowError('Missing flight identity');
        return {
          from_code: leg.from,
          to_code: leg.to,
          departure: localInstant(leg.departure, leg.from),
          arrival: localInstant(leg.arrival, leg.to),
          airline_code: leg.airline,
          airline_name: names[leg.airline] || (singleName as string) || leg.airline,
          flight_number: leg.flightNumber,
          duration_min: leg.duration,
        };
      });
      const quote: Quote = {
        segments,
        price: result.price,
        currency: result.currency as string,
        duration_min: result.duration,
        self_transfer: result.selfTransfer,
        checked_at: checked,
        verification: 'google_live',
        airline_verified: false,
        baggage: request.baggage,
        baggage_basis: 'google_search_filter',
        travelers: request.adults + request.children + request.infants,
        seat: request.seat,
        booking_url: url,
      };
      const key = fingerprint(quote);
      if (!seen.has(key)) {
        seen.add(key);
        quotes.push(quote);
      }
    } catch {
      rejected += 1;
    }
  }
  return [quotes, rejected];
}

function structuredCloneJson<T>(value: T): T {
  return JSON.parse(JSON.stringify(value));
}
