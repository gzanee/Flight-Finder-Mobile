/** Search form state and its request: port of values()/restoreForm() of static/weekends.js. */
import type { FlexibleRequest } from '../engine/flexible';
import { addDays, today } from '../engine/time';

export const NO_BUDGET = 100000;
export const NO_LIMIT_MINUTES = 4320;

/** A destination chip: one airport, or a whole country holding its airports. */
export type Place =
  | { kind: 'airport'; code: string; label: string }
  | { kind: 'country'; key: string; country: string; label: string; codes: string[]; cities: string[] };

export interface FormState {
  tripType: 'round_trip' | 'one_way';
  dateMode: 'fixed' | 'flexible';
  origins: Place[];
  anywhere: boolean;
  destinationSet: string;
  destinations: Place[];
  startDate: string;
  endDate: string;
  departureDays: number[];
  returnDays: number[];
  minNights: number;
  maxNights: number;
  departureMin: string;
  departureMax: string;
  returnMin: string;
  returnMax: string;
  adults: number;
  children: number;
  infants: number;
  seat: FlexibleRequest['seat'];
  baggage: FlexibleRequest['baggage'];
  budget: string; // empty: no limit
  maxStops: number;
  // Altri filtri
  departureArrivalMin: string;
  departureArrivalMax: string;
  returnArrivalMin: string;
  returnArrivalMax: string;
  finalArrival: string; // empty: none
  minHours: number;
  departureDurationMin: string; // hours
  departureDurationMax: string;
  returnDurationMin: string;
  returnDurationMax: string;
  minLayover: string; // minutes
  maxLayover: string;
  airlineMode: FlexibleRequest['airline_mode'];
  airlines: string;
  excludeBasicEconomy: boolean;
  selfTransfer: boolean;
  airportChange: boolean;
  schengenTransfers: boolean;
  returnAirport: FlexibleRequest['return_airport'];
  returnDeparture: FlexibleRequest['return_departure'];
}

export function initialForm(now = today()): FormState {
  return {
    tripType: 'round_trip',
    dateMode: 'flexible',
    origins: [
      { kind: 'airport', code: 'VCE', label: 'Venezia' },
      { kind: 'airport', code: 'TSF', label: 'Treviso' },
    ],
    anywhere: true,
    destinationSet: 'real',
    destinations: [],
    startDate: addDays(now, 1),
    endDate: addDays(now, 31),
    departureDays: [4],
    returnDays: [6],
    minNights: 2,
    maxNights: 2,
    departureMin: '00:00',
    departureMax: '23:59',
    returnMin: '00:00',
    returnMax: '23:59',
    adults: 1,
    children: 0,
    infants: 0,
    seat: 'economy',
    baggage: 'base',
    budget: '',
    maxStops: 1,
    departureArrivalMin: '00:00',
    departureArrivalMax: '23:59',
    returnArrivalMin: '00:00',
    returnArrivalMax: '23:59',
    finalArrival: '',
    minHours: 0,
    departureDurationMin: '0',
    departureDurationMax: '',
    returnDurationMin: '0',
    returnDurationMax: '',
    minLayover: '0',
    maxLayover: '',
    airlineMode: 'all',
    airlines: '',
    excludeBasicEconomy: false,
    selfTransfer: false,
    airportChange: false,
    schengenTransfers: false,
    returnAirport: 'same',
    returnDeparture: 'same',
  };
}

/** "Un weekend": Friday after 17, back on Sunday after 17, two nights. */
export function weekendPreset(f: FormState): FormState {
  return {
    ...f,
    tripType: 'round_trip',
    dateMode: 'flexible',
    departureDays: [4],
    returnDays: [6],
    minNights: 2,
    maxNights: 2,
    departureMin: '17:00',
    departureMax: '23:59',
    returnMin: '17:00',
    returnMax: '23:59',
  };
}

export function anytimePreset(f: FormState): FormState {
  return {
    ...f,
    departureDays: [0, 1, 2, 3, 4, 5, 6],
    returnDays: [0, 1, 2, 3, 4, 5, 6],
    departureMin: '00:00',
    departureMax: '23:59',
    returnMin: '00:00',
    returnMax: '23:59',
  };
}

/** Switching to exact dates keeps a sensible return, as on the web page. */
export function setDateMode(f: FormState, mode: FormState['dateMode']): FormState {
  if (mode === 'fixed' && f.endDate > addDays(f.startDate, 14)) return { ...f, dateMode: mode, endDate: addDays(f.startDate, 3) };
  return { ...f, dateMode: mode };
}

export function destinationCodes(f: FormState): string[] {
  return [...new Set(f.destinations.flatMap((p) => (p.kind === 'country' ? p.codes : [p.code])))];
}

const num = (value: string, fallback: number) => (value.trim() === '' ? fallback : Number(value.replace(',', '.')));

export class FormError extends Error {}

/** The request sent to the engine; the engine validates it. */
export function toSpec(f: FormState): Record<string, unknown> {
  if (!f.origins.length) throw new FormError('Da dove parti? Aggiungi almeno un aeroporto.');
  const round = f.tripType === 'round_trip';
  const flexible = f.dateMode === 'flexible';
  const destinations = f.anywhere ? [] : destinationCodes(f);
  if (!f.anywhere && !destinations.length) {
    throw new FormError('Dove vuoi andare? Aggiungi una destinazione oppure scegli Ovunque.');
  }
  const spec: Record<string, unknown> = {
    origins: f.origins.map((p) => (p.kind === 'airport' ? p.code : '')).filter(Boolean),
    destinations,
    trip_type: f.tripType,
    date_mode: f.dateMode,
    destination_set: f.destinationSet,
    start_date: f.startDate,
    end_date: round || flexible ? f.endDate : f.startDate,
    departure_days: f.departureDays,
    return_days: f.returnDays,
    min_nights: round && flexible ? f.minNights : 2,
    max_nights: round && flexible ? f.maxNights : 2,
    budget: num(f.budget, NO_BUDGET),
    adults: f.adults,
    children: f.children,
    infants: f.infants,
    max_stops: f.maxStops,
    return_airport: round ? f.returnAirport : 'same',
    return_departure: round && !f.anywhere ? f.returnDeparture : 'same',
    baggage: f.baggage,
    seat: f.seat,
    airline_mode: f.airlineMode,
    airlines: f.airlines,
    allow_self_transfer: f.selfTransfer,
    allow_airport_change: f.airportChange,
    exclude_basic_economy: f.excludeBasicEconomy,
    schengen_transfers_only: f.schengenTransfers,
    final_arrival_max: f.finalArrival || null,
    min_destination_hours: round ? f.minHours : 0,
    min_layover_minutes: num(f.minLayover, 0),
    max_layover_minutes: num(f.maxLayover, NO_LIMIT_MINUTES),
    departure_min: f.departureMin,
    departure_max: f.departureMax,
    return_min: f.returnMin,
    return_max: f.returnMax,
    departure_arrival_min: f.departureArrivalMin || '00:00',
    departure_arrival_max: f.departureArrivalMax || '23:59',
    return_arrival_min: f.returnArrivalMin || '00:00',
    return_arrival_max: f.returnArrivalMax || '23:59',
    departure_duration_min: Math.round(num(f.departureDurationMin, 0) * 60),
    departure_duration_max: Math.round(num(f.departureDurationMax, 72) * 60),
    return_duration_min: Math.round(num(f.returnDurationMin, 0) * 60),
    return_duration_max: Math.round(num(f.returnDurationMax, 72) * 60),
  };
  if (!round) {
    Object.assign(spec, {
      return_min: '00:00',
      return_max: '23:59',
      return_arrival_min: '00:00',
      return_arrival_max: '23:59',
      return_duration_min: 0,
      return_duration_max: NO_LIMIT_MINUTES,
    });
  }
  return spec;
}

/** The form of a saved search, to search again or change it. */
export function fromRequest(r: FlexibleRequest, label: (code: string) => string): FormState {
  const hours = (minutes: number, none: boolean) => (none ? '' : String(minutes / 60));
  return {
    tripType: r.trip_type,
    dateMode: r.date_mode,
    origins: r.origins.map((code) => ({ kind: 'airport', code, label: label(code) })),
    anywhere: !r.destinations.length,
    destinationSet: r.destination_set,
    destinations: r.destinations.map((code) => ({ kind: 'airport', code, label: label(code) })),
    startDate: r.start_date,
    endDate: r.end_date,
    departureDays: [...r.departure_days],
    returnDays: [...r.return_days],
    minNights: r.min_nights,
    maxNights: r.max_nights,
    departureMin: r.departure_min,
    departureMax: r.departure_max,
    returnMin: r.return_min,
    returnMax: r.return_max,
    adults: r.adults,
    children: r.children,
    infants: r.infants,
    seat: r.seat,
    baggage: r.baggage,
    budget: r.budget === NO_BUDGET ? '' : String(r.budget),
    maxStops: r.max_stops,
    departureArrivalMin: r.departure_arrival_min,
    departureArrivalMax: r.departure_arrival_max,
    returnArrivalMin: r.return_arrival_min,
    returnArrivalMax: r.return_arrival_max,
    finalArrival: r.final_arrival_max ?? '',
    minHours: r.min_destination_hours,
    departureDurationMin: String(r.departure_duration_min / 60),
    departureDurationMax: hours(r.departure_duration_max, r.departure_duration_max >= NO_LIMIT_MINUTES),
    returnDurationMin: String(r.return_duration_min / 60),
    returnDurationMax: hours(r.return_duration_max, r.return_duration_max >= NO_LIMIT_MINUTES),
    minLayover: String(r.min_layover_minutes),
    maxLayover: r.max_layover_minutes >= NO_LIMIT_MINUTES ? '' : String(r.max_layover_minutes),
    airlineMode: r.airline_mode,
    airlines: r.airlines.join(', '),
    excludeBasicEconomy: r.exclude_basic_economy,
    selfTransfer: r.allow_self_transfer,
    airportChange: r.allow_airport_change,
    schengenTransfers: r.schengen_transfers_only,
    returnAirport: r.return_airport,
    returnDeparture: r.return_departure,
  };
}
