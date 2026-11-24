/**
 * Google Flights `tfs` query: the protobuf message fast-flights builds, written
 * by hand. Field numbers and order follow fast_flights/pb/flights.proto and
 * Python protobuf's serializer (ascending field number, packed repeated enums,
 * proto3 `optional` fields written whenever set, plain scalars only when non-zero).
 *
 * Several airports per side repeat the Airport message, as AirportGroupQuery
 * does in flightcore/live_quotes.py: with one airport the bytes are identical.
 */

export type Seat = 'economy' | 'premium-economy' | 'business' | 'first';
export type Trip = 'round-trip' | 'one-way';

const SEAT: Record<Seat, number> = { economy: 1, 'premium-economy': 2, business: 3, first: 4 };
const TRIP: Record<Trip, number> = { 'round-trip': 1, 'one-way': 2 };
const ADULT = 1;
const CHILD = 2;
const INFANT_ON_LAP = 4;

export interface Leg {
  date: string; // YYYY-MM-DD
  from: string[]; // airport codes, one or more
  to: string[];
  maxStops?: number;
  airlines?: string[];
  earliestDepartureHour?: number;
  latestDepartureHour?: number;
}

export interface QueryOptions {
  legs: Leg[];
  trip: Trip;
  seat: Seat;
  adults: number;
  children: number;
  infants: number;
  language: string;
  currency: string;
  carryOnBags?: number;
  checkedBags?: number;
  hideSeparateAndSelfTransfer?: boolean;
  excludeBasicEconomy?: boolean;
}

class Writer {
  bytes: number[] = [];

  varint(value: number) {
    let n = value;
    if (n < 0) {
      // int32 negatives are 10-byte two's complement; never needed here.
      throw new Error('negative varint');
    }
    do {
      let low = n % 128;
      n = Math.floor(n / 128);
      if (n) low |= 0x80;
      this.bytes.push(low);
    } while (n);
    return this;
  }

  tag(field: number, wire: number) {
    return this.varint(field * 8 + wire);
  }

  int(field: number, value: number | undefined) {
    if (value === undefined || value === null) return this;
    return this.tag(field, 0).varint(value);
  }

  bool(field: number, value: boolean | undefined) {
    if (!value) return this;
    return this.tag(field, 0).varint(1);
  }

  bytesField(field: number, payload: number[]) {
    this.tag(field, 2).varint(payload.length);
    for (const b of payload) this.bytes.push(b);
    return this;
  }

  string(field: number, value: string | undefined) {
    if (!value) return this;
    return this.bytesField(field, utf8(value));
  }

  packed(field: number, values: number[]) {
    if (!values.length) return this;
    const inner = new Writer();
    for (const v of values) inner.varint(v);
    return this.bytesField(field, inner.bytes);
  }
}

function utf8(text: string): number[] {
  const out: number[] = [];
  for (const ch of unescape(encodeURIComponent(text))) out.push(ch.charCodeAt(0));
  return out;
}

function airport(code: string): number[] {
  return new Writer().string(2, code).bytes;
}

function encodeLeg(leg: Leg): number[] {
  const w = new Writer().string(2, leg.date).int(5, leg.maxStops);
  for (const airline of leg.airlines ?? []) w.string(6, airline);
  w.int(8, leg.earliestDepartureHour).int(9, leg.latestDepartureHour);
  for (const code of leg.from) w.bytesField(13, airport(code));
  for (const code of leg.to) w.bytesField(14, airport(code));
  return w.bytes;
}

export function encodeQuery(q: QueryOptions): number[] {
  const w = new Writer();
  for (const leg of q.legs) w.bytesField(3, encodeLeg(leg));
  w.packed(8, [
    ...Array(q.adults).fill(ADULT),
    ...Array(q.children).fill(CHILD),
    ...Array(q.infants).fill(INFANT_ON_LAP),
  ]);
  w.int(9, SEAT[q.seat]);
  if (q.carryOnBags || q.checkedBags) {
    const bag = new Writer().int(2, q.carryOnBags ?? 0).int(3, q.checkedBags ?? 0);
    w.bytesField(13, bag.bytes);
  }
  w.bool(17, q.hideSeparateAndSelfTransfer);
  w.int(19, TRIP[q.trip]);
  w.bool(25, q.excludeBasicEconomy);
  return w.bytes;
}

const B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';

export function base64(bytes: number[]): string {
  let out = '';
  for (let i = 0; i < bytes.length; i += 3) {
    const [a, b, c] = [bytes[i], bytes[i + 1], bytes[i + 2]];
    const n = (a << 16) | ((b ?? 0) << 8) | (c ?? 0);
    out += B64[(n >> 18) & 63] + B64[(n >> 12) & 63];
    out += b === undefined ? '=' : B64[(n >> 6) & 63];
    out += c === undefined ? '=' : B64[n & 63];
  }
  return out;
}

export function tfs(q: QueryOptions): string {
  return base64(encodeQuery(q));
}

/** Page the app downloads; also the link the user opens to book. */
export function searchUrl(q: QueryOptions, path = '/travel/flights'): string {
  const params = `tfs=${encodeURIComponent(tfs(q))}&hl=${q.language}&curr=${q.currency}`;
  return `https://www.google.com${path}?${params}`;
}
