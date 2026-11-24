/**
 * Airport-local timestamps as Python's datetime.isoformat() writes them:
 * "2026-10-02T18:00:00+02:00". Wall-clock parts are read from the string,
 * elapsed times from the instant, exactly as flightcore does.
 */
import { airportZone } from './airports';

const formatters = new Map<string, Intl.DateTimeFormat>();

function formatter(zone: string) {
  let f = formatters.get(zone);
  if (!f) {
    f = new Intl.DateTimeFormat('en-US', {
      timeZone: zone,
      hourCycle: 'h23',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
    formatters.set(zone, f);
  }
  return f;
}

/** Offset of `zone` from UTC at the instant `utcMs`, in minutes. */
export function zoneOffset(zone: string, utcMs: number): number {
  const parts: Record<string, number> = {};
  for (const p of formatter(zone).formatToParts(new Date(utcMs))) {
    if (p.type !== 'literal') parts[p.type] = Number(p.value);
  }
  const wall = Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour % 24, parts.minute, parts.second);
  return Math.round((wall - Math.floor(utcMs / 1000) * 1000) / 60000);
}

function pad(n: number, size = 2) {
  return String(n).padStart(size, '0');
}

export function formatOffset(minutes: number): string {
  const sign = minutes < 0 ? '-' : '+';
  const abs = Math.abs(minutes);
  return `${sign}${pad(Math.floor(abs / 60))}:${pad(abs % 60)}`;
}

/** "YYYY-MM-DDTHH:MM[:SS]" (no offset) as UTC milliseconds of the same wall clock. */
function naiveMs(naive: string): number {
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?/.exec(naive);
  if (!m) throw new Error(`Orario non valido: ${naive}`);
  return Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +(m[6] ?? 0));
}

/**
 * Local wall time at an airport as an ISO string with its offset.
 * Like flexible.local_instant: ambiguous or non-existent times (DST change)
 * are refused, never guessed.
 */
export function localInstantIn(naive: string, zone: string): string {
  const wall = naiveMs(naive);
  // Candidate offsets: those in force a day before and after cover any change.
  const offsets = new Set([zoneOffset(zone, wall - 86400000), zoneOffset(zone, wall + 86400000)]);
  const valid = [...offsets].filter((off) => zoneOffset(zone, wall - off * 60000) === off);
  if (valid.length === 0) throw new Error('Orario locale inesistente.');
  if (valid.length > 1) throw new Error('Orario ambiguo o inesistente al cambio di ora.');
  const d = new Date(wall);
  return (
    `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}` +
    `T${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}:${pad(d.getUTCSeconds())}` +
    formatOffset(valid[0])
  );
}

export function localInstant(naive: string, airport: string): string {
  const zone = airportZone(airport);
  if (!zone) throw new Error(`Fuso orario sconosciuto per ${airport}`);
  return localInstantIn(naive, zone);
}

/** Instant of an ISO string with offset, in ms. NaN if it has no offset. */
export function instant(iso: string): number {
  if (!/(?:[+-]\d{2}:\d{2}|Z)$/.test(iso)) return Number.NaN;
  return Date.parse(iso);
}

export function hasOffset(iso: string): boolean {
  return /(?:[+-]\d{2}:\d{2}|Z)$/.test(iso);
}

/** Local calendar date and clock of an ISO timestamp, as written. */
export function localDate(iso: string): string {
  return iso.slice(0, 10);
}

export function localClock(iso: string): string {
  return iso.slice(11, 19).padEnd(8, ':00').slice(0, 8);
}

/** "HH:MM" -> "HH:MM:00", for comparisons with localClock. */
export function clockSeconds(hhmm: string): string {
  return hhmm.length === 5 ? `${hhmm}:00` : hhmm;
}

export function minutesBetween(fromIso: string, toIso: string): number {
  return (instant(toIso) - instant(fromIso)) / 60000;
}

// ---- Calendar dates, without time zones ----

export function parseDay(day: string): number {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(day)) throw new Error(`Data non valida: ${day}`);
  const ms = Date.UTC(+day.slice(0, 4), +day.slice(5, 7) - 1, +day.slice(8, 10));
  if (formatDay(ms) !== day) throw new Error(`Data non valida: ${day}`);
  return ms;
}

export function formatDay(ms: number): string {
  const d = new Date(ms);
  return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`;
}

export function addDays(day: string, days: number): string {
  return formatDay(parseDay(day) + days * 86400000);
}

export function daysBetween(from: string, to: string): number {
  return Math.round((parseDay(to) - parseDay(from)) / 86400000);
}

/** Python weekday(): Monday 0 ... Sunday 6. */
export function weekday(day: string): number {
  return (new Date(parseDay(day)).getUTCDay() + 6) % 7;
}

/** Today's date on the device. */
export function today(now = new Date()): string {
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

export function utcNow(): string {
  // Python isoformat: "2026-11-24T10:00:00.123456+00:00"
  return new Date().toISOString().replace('Z', '+00:00');
}
