/** Google Flights queries for the engine: port of LiveQuotes._leg/_query. */
import { type Leg, type QueryOptions, type Seat, searchUrl, tfs } from './query';

const HOURS_PER_DAY = 24;

/** The request fields a Google query depends on (snake_case as in Python). */
export interface QueryRequest {
  adults: number;
  children: number;
  infants: number;
  seat: Seat;
  max_stops: number;
  baggage: 'base' | 'cabin' | 'checked';
  allow_self_transfer: boolean;
  exclude_basic_economy: boolean;
  airlines: readonly string[];
  departure_min: string;
  departure_max: string;
  return_min?: string;
  return_max?: string;
  currency: string;
}

export function leg(
  origins: readonly string[],
  destinations: readonly string[],
  day: string,
  request: QueryRequest,
  inbound: boolean,
): Leg {
  const lo = (inbound ? request.return_min : request.departure_min) ?? '00:00';
  const hi = (inbound ? request.return_max : request.departure_max) ?? '23:59';
  // Google accepts whole hours. Widen the upper bound before applying our
  // exact minute filter so 19:59 does not become a cutoff at 19:00.
  const upper = Number(hi.slice(0, 2)) + (hi.slice(3) !== '00' ? 1 : 0);
  return {
    date: day,
    from: [...origins],
    to: [...destinations],
    maxStops: request.max_stops,
    airlines: request.airlines.length ? [...request.airlines] : undefined,
    earliestDepartureHour: Number(lo.slice(0, 2)),
    latestDepartureHour: upper < HOURS_PER_DAY ? upper : undefined,
  };
}

export function query(
  request: QueryRequest,
  legs: Leg[],
  trip: 'one-way' | 'round-trip',
): QueryOptions {
  const bags = request.adults + request.children;
  return {
    legs,
    trip,
    seat: request.seat,
    adults: request.adults,
    children: request.children,
    infants: request.infants,
    language: 'it',
    currency: request.currency,
    carryOnBags: request.baggage === 'cabin' ? bags : 0,
    checkedBags: request.baggage === 'checked' ? bags : 0,
    hideSeparateAndSelfTransfer: !request.allow_self_transfer,
    excludeBasicEconomy: request.exclude_basic_economy,
  };
}

export function oneWayQuery(
  origins: readonly string[],
  destinations: readonly string[],
  day: string,
  request: QueryRequest,
  inbound = false,
): QueryOptions {
  return query(request, [leg(origins, destinations, day, request, inbound)], 'one-way');
}

export function roundTripQuery(
  origin: string,
  destination: string,
  out: string,
  back: string,
  request: QueryRequest,
): QueryOptions {
  return query(
    request,
    [
      leg([origin], [destination], out, request, false),
      leg([destination], [origin], back, request, true),
    ],
    'round-trip',
  );
}

/** fast-flights Query.url(): the link shown to the user (tfs not escaped). */
export function bookingUrl(q: QueryOptions): string {
  return `https://www.google.com/travel/flights/search?tfs=${tfs(q)}&hl=${q.language}&curr=${q.currency}`;
}

export { searchUrl, tfs };
