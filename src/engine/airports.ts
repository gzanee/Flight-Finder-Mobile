/** Offline airport catalog: port of flightcore/airports.py (data from tools/export_data.py). */
import catalogData from './data/airports.json';
import places from './data/places.json';
import routeSnapshot from './data/routes.json';

// code -> [catalog name, city, display name, country, time zone, known to fli]
type Row = [string, string, string, string, string, number];
const CATALOG = catalogData as unknown as Record<string, Row>;
const SNAPSHOT = routeSnapshot as unknown as Record<string, string[]>;

export const HOME_AIRPORTS: Record<string, string> = places.home;
export const DESTINATION_SETS: Record<string, { label: string; codes: string[] }> = places.sets;
const CITY_ALIASES: Record<string, string[]> = places.aliases;
const MAIN_AIRPORTS: Record<string, string[]> = places.main;
export const DEFAULT_DESTINATION_SET = 'europe-short';

export interface AirportDetails {
  code: string;
  city: string;
  name: string;
  country: string;
}

export function exists(code: string): boolean {
  return Boolean(CATALOG[code.toUpperCase()]?.[0]);
}

export function name(code: string): string | undefined {
  return CATALOG[code.toUpperCase()]?.[0] || undefined;
}

/** Codes fli's decoder accepts: rows with others are rejected, like in Python. */
export function knownToDecoder(code: string): boolean {
  return CATALOG[code]?.[5] === 1;
}

export function airportZone(code: string): string | undefined {
  return CATALOG[code]?.[4] || undefined;
}

export function airportCountry(code: string): string | undefined {
  return CATALOG[code]?.[3] || undefined;
}

export function details(raw: string): AirportDetails {
  const code = raw.toUpperCase();
  const row = CATALOG[code];
  if (row?.[1]) {
    return { code, city: row[1], name: row[2], country: row[3].toLowerCase() };
  }
  const label = row?.[0] || 'Aeroporto non identificato';
  return { code, city: label, name: label, country: (row?.[3] ?? '').toLowerCase() };
}

/** Lower case without accents, so "zurigo" finds "Zürich". */
export function normalize(text: string): string {
  return text
    .toLowerCase()
    .normalize('NFKD')
    .replace(/[̀-ͯ]/g, '');
}

let normalizedNames: [string, string, string][] | null = null;

export interface AirportHit {
  code: string;
  name: string;
  city: string;
  home: boolean;
}

/** Autocomplete on code and name: exact code first, then prefix, then substring. */
export function search(keyword: string, limit = 10): AirportHit[] {
  const kw = (keyword ?? '').trim();
  if (kw.length < 2) return [];
  const needle = normalize(kw);
  const aliasHits = Object.entries(CITY_ALIASES)
    .filter(([alias]) => alias.startsWith(needle) || needle === alias)
    .flatMap(([, codes]) => codes);
  if (!normalizedNames) {
    normalizedNames = Object.entries(CATALOG)
      .filter(([, row]) => row[0])
      .map(([code, row]) => [code, row[0], normalize(row[0])]);
  }
  const scored: [number, string, string][] = [];
  for (const [code, label, haystack] of normalizedNames) {
    let score: number;
    if (needle === code.toLowerCase()) score = 0;
    else if (haystack.startsWith(needle)) score = 1;
    else if (haystack.includes(needle)) score = 2;
    else if (code.toLowerCase().startsWith(needle)) score = 3;
    else continue;
    scored.push([score, label, code]);
  }
  scored.sort((a, b) => a[0] - b[0] || a[1].length - b[1].length || (a[1] < b[1] ? -1 : a[1] > b[1] ? 1 : 0));
  const final: string[] = [];
  for (const code of [...aliasHits, ...scored.map((s) => s[2])]) {
    if (!final.includes(code) && exists(code)) final.push(code);
    if (final.length >= limit) break;
  }
  return final.map((code) => ({
    code,
    name: name(code) as string,
    city: details(code).city,
    home: code in HOME_AIRPORTS,
  }));
}

export function destinationSet(key: string): string[] {
  const set = DESTINATION_SETS[key] ?? DESTINATION_SETS[DEFAULT_DESTINATION_SET];
  return [...new Set(set.codes)].filter(exists);
}

/** Snapshot routes of an origin (the app has no Wikipedia refresh). */
export function routeDestinations(origin: string): string[] {
  const codes = SNAPSHOT[origin.toUpperCase()];
  return Array.isArray(codes) ? [...codes] : [];
}

export function routePool(): string[] {
  const pool = new Set<string>();
  for (const codes of Object.values(SNAPSHOT)) if (Array.isArray(codes)) codes.forEach((c) => pool.add(c));
  return [...pool].sort();
}

export interface CountryGroup {
  linked: boolean;
  airports: { code: string; city: string; name: string }[];
}

/** Airports to pick a whole country at once, by ISO country code (see airports.countries). */
export function countries(origins: string[]): Record<string, CountryGroup> {
  const pool = new Set(routePool());
  let linked = new Set(origins.flatMap((o) => routeDestinations(o)));
  if (!linked.size) linked = pool;
  const known = new Set(pool);
  for (const set of Object.values(DESTINATION_SETS)) set.codes.forEach((c) => known.add(c));
  for (const codes of Object.values(MAIN_AIRPORTS)) codes.forEach((c) => known.add(c));
  const upperOrigins = origins.map((o) => o.toUpperCase());
  const grouped: Record<string, CountryGroup> = {};
  const unlinked = [...known].filter((c) => !linked.has(c));
  for (const [isLinked, codes] of [
    [true, [...linked]],
    [false, unlinked],
  ] as const) {
    for (const code of [...codes].sort()) {
      const info = details(code);
      if (!exists(code) || !info.country || upperOrigins.includes(code.toUpperCase())) continue;
      grouped[info.country] ??= { linked: isLinked, airports: [] };
      const entry = grouped[info.country];
      if (entry.linked === isLinked) entry.airports.push({ code, city: info.city, name: info.name });
    }
  }
  return grouped;
}

/** Candidate destinations of an "Ovunque" search, per origin (flexible_api.candidate_routes). */
export function candidateRoutes(spec: {
  destinations: readonly string[];
  destination_set: string;
  origins: readonly string[];
}): Record<string, string[]> {
  if (spec.destinations.length) return {};
  if (spec.destination_set !== 'real') {
    return Object.fromEntries(spec.origins.map((o) => [o, destinationSet(spec.destination_set)]));
  }
  const map = Object.fromEntries(spec.origins.map((o) => [o, routeDestinations(o)]));
  if (!Object.values(map).some((v) => v.length)) return { catalogo_generale: routePool() };
  return map;
}
