/**
 * Live Google quotes over the device's own connection: port of LiveQuotes
 * (flightcore/live_quotes.py) and of the consent cookie flow (consent.py).
 *
 * One request at a time, a pause after every answer, no automatic retry of a
 * 403/429, and a five-minute cache: the same rules as the Python engine.
 */
import { type ExpectedRoute, LiveBlocked, LiveTransient, LiveUnavailable, decode } from './decode';
import {
  type FlexibleRequest,
  MAX_AIRPORTS_PER_SEARCH,
  type Quote,
  airportsPerSearch,
  fingerprint,
  pairBatches,
} from './flexible';
import { bookingUrl, oneWayQuery, roundTripQuery, searchUrl } from './google';
import type { QueryOptions } from './query';

export { LiveBlocked, LiveTransient, LiveUnavailable };

export const QUOTE_TTL = 300;
const CACHE_LIMIT = 2000;
const NETWORK_RETRIES = 2;
const GOOGLE_ERROR_RETRIES = 5;
const MAX_GOOGLE_ERROR_WAIT = 30;
export const DEFAULT_REQUEST_INTERVAL = 0.3;
const REQUEST_OVERHEAD = 0.45;
const BATCH_REQUEST_OVERHEAD = 0.75;
const BLOCK_SECONDS = 900;

export function secondsPerSearch(request: Pick<FlexibleRequest, 'max_stops'>, interval = DEFAULT_REQUEST_INTERVAL) {
  return interval + (airportsPerSearch(request) > 1 ? BATCH_REQUEST_OVERHEAD : REQUEST_OVERHEAD);
}

export interface HttpResponse {
  status: number;
  url: string;
  text: string;
  setCookies?: string[];
}

export interface Http {
  get(url: string, headers: Record<string, string>): Promise<HttpResponse>;
  postForm(url: string, fields: Record<string, string>, headers: Record<string, string>): Promise<HttpResponse>;
}

export interface Clock {
  now(): number; // seconds, monotonic enough
  sleep(seconds: number): Promise<void>;
}

export const realClock: Clock = {
  now: () => Date.now() / 1000,
  sleep: (s) => new Promise((resolve) => setTimeout(resolve, s * 1000)),
};

/**
 * Google's "Reject all" consent: the SOCS value that the consent form sets,
 * as sent by many open-source clients. Used until a handshake gives a fresh one.
 */
export const DEFAULT_SOCS = 'CAESEwgDEgk0ODE3Nzk3MjQaAmVuIAEaBgiA_LyaBg';
const RELEVANT_COOKIES = ['SOCS', 'CONSENT', '__Secure-ENID'];
const PROBE_URL = 'https://www.google.com/travel/flights?hl=en&gl=IT&curr=EUR';
const CONSENT_SAVE_URL = 'https://consent.google.com/save';
const BASE_HEADERS = {
  'Accept-Language': 'it-IT,it;q=0.9,en;q=0.8',
  Accept: 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
};

export interface CookieStore {
  load(): Promise<Record<string, string> | null>;
  save(cookies: Record<string, string>): Promise<void>;
}

export const memoryCookies = (): CookieStore => {
  let saved: Record<string, string> | null = null;
  return {
    load: async () => saved,
    save: async (c) => {
      saved = c;
    },
  };
};

function decodeEntities(text: string): string {
  return text
    .replace(/&#(\d+);/g, (_, n) => String.fromCharCode(Number(n)))
    .replace(/&#x([0-9a-f]+);/gi, (_, n) => String.fromCharCode(parseInt(n, 16)))
    .replace(/&quot;/g, '"')
    .replace(/&#39;|&apos;/g, "'")
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&');
}

export function hiddenInputs(block: string): Record<string, string> {
  const fields: Record<string, string> = {};
  for (const m of block.matchAll(/<input[^>]*type="hidden"[^>]*>/g)) {
    const tag = m[0];
    const name = /name="([^"]+)"/.exec(tag);
    const value = /value="([^"]*)"/.exec(tag);
    if (name) fields[name[1]] = decodeEntities(value ? value[1] : '');
  }
  return fields;
}

function parseSetCookies(lines: string[] | undefined): Record<string, string> {
  const out: Record<string, string> = {};
  for (const line of lines ?? []) {
    // Several cookies can arrive joined by ", " on one line.
    for (const part of line.split(/,(?=\s*[A-Za-z0-9_\-]+=)/)) {
      const m = /^\s*([^=;\s]+)=([^;]*)/.exec(part);
      if (m && RELEVANT_COOKIES.includes(m[1])) out[m[1]] = m[2];
    }
  }
  return out;
}

export class Consent {
  private cookies: Record<string, string> | null = null;
  constructor(
    private http: Http,
    private store: CookieStore,
  ) {}

  async get(): Promise<Record<string, string>> {
    if (this.cookies) return this.cookies;
    const saved = await this.store.load().catch(() => null);
    this.cookies = saved && saved.SOCS ? saved : { SOCS: DEFAULT_SOCS };
    return this.cookies;
  }

  /** Reject-all handshake, like consent._perform_handshake. Falls back to the default cookie. */
  async refresh(): Promise<Record<string, string>> {
    let cookies: Record<string, string> = { SOCS: DEFAULT_SOCS };
    try {
      const probe = await this.http.get(PROBE_URL, { ...BASE_HEADERS, 'Accept-Language': 'en-US,en;q=0.9' });
      const page = probe.text;
      if (page.slice(0, 2000).includes('consent.google.com') || probe.url.includes('consent.google.com')) {
        const anchor = page.indexOf('set_eom" value="true"');
        if (anchor >= 0) {
          const form = page.slice(page.lastIndexOf('<form', anchor), page.indexOf('</form>', anchor));
          const saved = await this.http.postForm(CONSENT_SAVE_URL, hiddenInputs(form), {
            'Accept-Language': 'en-US,en;q=0.9',
          });
          const fresh = parseSetCookies(saved.setCookies);
          if (fresh.SOCS) cookies = fresh;
        }
      }
    } catch {
      // Keep the default consent cookie.
    }
    this.cookies = cookies;
    await this.store.save(cookies).catch(() => undefined);
    return cookies;
  }

  header(cookies: Record<string, string>): string {
    return Object.entries(cookies)
      .map(([k, v]) => `${k}=${v}`)
      .join('; ');
  }
}

export interface RouteResult {
  quotes: Quote[];
  unverified: number;
  empty: boolean;
}

type CacheEntry = [number, RouteResult];

export class LiveQuotes {
  private cache = new Map<string, CacheEntry>();
  private queue: Promise<unknown> = Promise.resolve();
  private nextRequest = 0;
  blockedUntil = 0;
  requestInterval = DEFAULT_REQUEST_INTERVAL;
  readonly consent: Consent;

  constructor(
    private http: Http,
    cookies: CookieStore = memoryCookies(),
    private clock: Clock = realClock,
  ) {
    this.consent = new Consent(http, cookies);
  }

  clearCache() {
    this.cache.clear();
  }

  async search(origin: string, destination: string, day: string, r: FlexibleRequest, inbound = false, fresh = false) {
    const found = await this.searchMany([origin], [destination], day, r, inbound, fresh);
    return found.get(`${origin}|${destination}`) as RouteResult;
  }

  /** Live one-way quotes of every origin-destination pair, by "from|to" key. */
  async searchMany(
    origins: string[],
    destinations: string[],
    day: string,
    r: FlexibleRequest,
    inbound = false,
    fresh = false,
  ): Promise<Map<string, RouteResult>> {
    const pairs: [string, string][] = [];
    for (const o of origins) for (const d of destinations) if (o !== d) pairs.push([o, d]);
    const keys = new Map(pairs.map((p) => [p.join('|'), this.key(p[0], p[1], day, r, inbound)]));
    const results = new Map<string, RouteResult>();
    const missing: [string, string][] = [];
    const now = this.clock.now();
    for (const pair of pairs) {
      const hit = this.cache.get(keys.get(pair.join('|'))!);
      if (!fresh && hit && now - hit[0] < QUOTE_TTL) results.set(pair.join('|'), hit[1]);
      else missing.push(pair);
    }
    const size = Math.min(airportsPerSearch(r), MAX_AIRPORTS_PER_SEARCH);
    for (const [os, ds] of pairBatches(missing, size)) {
      const found = await this.requestGroup(os, ds, day, r, inbound);
      for (const [pair, result] of found) {
        if (this.cache.size >= CACHE_LIMIT) this.cache.delete(this.cache.keys().next().value as string);
        this.cache.set(keys.get(pair) ?? this.key(...(pair.split('|') as [string, string]), day, r, inbound), [
          this.clock.now(),
          result,
        ]);
        results.set(pair, result);
      }
    }
    return new Map(pairs.map((p) => [p.join('|'), results.get(p.join('|')) as RouteResult]));
  }

  private key(origin: string, destination: string, day: string, r: FlexibleRequest, inbound: boolean) {
    const prefix = inbound ? 'return' : 'departure';
    return JSON.stringify([
      origin,
      destination,
      day,
      r[`${prefix}_min`],
      r[`${prefix}_max`],
      r.adults,
      r.children,
      r.max_stops,
      r.allow_self_transfer,
      r.baggage,
      r.currency,
      r.infants,
      r.seat,
      r.airlines,
      r.exclude_basic_economy,
    ]);
  }

  /** One Google request; a group Google cannot answer is retried pair by pair. */
  private async requestGroup(origins: string[], destinations: string[], day: string, r: FlexibleRequest, inbound: boolean) {
    try {
      return await this.request(origins, destinations, day, r, inbound);
    } catch (exc) {
      if (exc instanceof LiveBlocked) throw exc;
      if (!(exc instanceof LiveUnavailable) || (origins.length === 1 && destinations.length === 1)) throw exc;
    }
    const found = new Map<string, RouteResult>();
    for (const o of origins) {
      for (const d of destinations) {
        if (o !== d) for (const [k, v] of await this.request([o], [d], day, r, inbound)) found.set(k, v);
      }
    }
    return found;
  }

  private async request(origins: string[], destinations: string[], day: string, r: FlexibleRequest, inbound: boolean) {
    const q = oneWayQuery(origins, destinations, day, r, inbound);
    let quotes: Quote[] = [];
    let rejected = 0;
    for (let attempt = 0; attempt <= GOOGLE_ERROR_RETRIES; attempt++) {
      const html = await this.fetchPage(q);
      try {
        const expected: ExpectedRoute = [origins, destinations];
        [quotes, rejected] = decode(html, r, bookingUrl(q), expected);
        break;
      } catch (exc) {
        if (!(exc instanceof LiveTransient) || attempt === GOOGLE_ERROR_RETRIES) throw exc;
        await this.clock.sleep(Math.min(MAX_GOOGLE_ERROR_WAIT, 2 ** (attempt + 1)));
      }
    }
    const byPair = new Map<string, Quote[]>();
    for (const o of origins) for (const d of destinations) if (o !== d) byPair.set(`${o}|${d}`, []);
    for (const quote of quotes) {
      const pair = `${quote.segments[0].from_code}|${quote.segments[quote.segments.length - 1].to_code}`;
      const list = byPair.get(pair);
      if (list) list.push(quote);
      else rejected += 1; // an offer for airports nobody asked about
    }
    const out = new Map<string, RouteResult>();
    let index = 0;
    for (const [pair, found] of byPair) {
      out.set(pair, { quotes: found, unverified: index === 0 ? rejected : 0, empty: !found.length && !rejected });
      index++;
    }
    return out;
  }

  /** Single-ticket return fares: {outbound fingerprint: cheapest total}, or null. */
  async roundTrip(origin: string, destination: string, out: string, back: string, r: FlexibleRequest) {
    const q = roundTripQuery(origin, destination, out, back, r);
    let quotes: Quote[];
    try {
      [quotes] = decode(await this.fetchPage(q), r, bookingUrl(q));
    } catch (exc) {
      if (exc instanceof LiveBlocked) throw exc;
      if (exc instanceof LiveUnavailable) return null;
      throw exc;
    }
    const fares: Record<string, number> = {};
    for (const quote of quotes) {
      const s = quote.segments;
      if (s[0].from_code === origin && s[s.length - 1].to_code === destination) {
        const key = fingerprint(quote);
        fares[key] = Math.min(quote.price, fares[key] ?? quote.price);
      }
    }
    return fares;
  }

  /** Serialized: one request every interval, no parallel burst. */
  fetchPage(q: QueryOptions): Promise<string> {
    const run = this.queue.then(() => this.fetchNow(q));
    this.queue = run.catch(() => undefined);
    return run;
  }

  private async fetchNow(q: QueryOptions): Promise<string> {
    if (this.clock.now() < this.blockedUntil) {
      throw new LiveBlocked('Google limita le richieste. Ricerca fermata; riprova più tardi.');
    }
    const wait = this.nextRequest - this.clock.now();
    if (wait > 0) await this.clock.sleep(wait);
    let consentRetried = false;
    let response: HttpResponse | null = null;
    for (let attempt = 0; attempt <= NETWORK_RETRIES; attempt++) {
      try {
        const cookies = await this.consent.get();
        response = await this.http.get(searchUrl(q), { ...BASE_HEADERS, Cookie: this.consent.header(cookies) });
        if (!consentRetried && isConsentPage(response)) {
          // The saved consent is not accepted any more: renew it once.
          consentRetried = true;
          await this.consent.refresh();
          attempt--;
          continue;
        }
        break;
      } catch (exc) {
        if (attempt === NETWORK_RETRIES) {
          throw new LiveUnavailable('Connessione a Google non riuscita dopo vari tentativi. Puoi premere Riprendi.');
        }
        await this.clock.sleep(1.5 * (attempt + 1));
      }
    }
    const res = response as HttpResponse;
    this.nextRequest = this.clock.now() + this.requestInterval;
    if (
      res.status === 403 ||
      res.status === 429 ||
      res.url.includes('/sorry/') ||
      res.text.slice(0, 6000).toLowerCase().includes('unusual traffic')
    ) {
      this.blockedUntil = this.clock.now() + BLOCK_SECONDS;
      throw new LiveBlocked('Google ha limitato le richieste. Nessun tentativo automatico.');
    }
    if (res.status !== 200) throw new LiveUnavailable(`Google ha risposto HTTP ${res.status}.`);
    return res.text;
  }
}

function isConsentPage(res: HttpResponse): boolean {
  return res.url.includes('consent.google.com') || res.text.slice(0, 2000).includes('consent.google.com/');
}

/** Google Flights page for the same airports and dates as one A/R search. */
export function roundTripUrl(trip: { outbound: Quote; inbound: Quote | null }, r: FlexibleRequest): string | null {
  const back = trip.inbound;
  if (!back) return null;
  const out = trip.outbound.segments;
  const origin = out[0].from_code;
  const destination = out[out.length - 1].to_code;
  const b = back.segments;
  if (b[0].from_code !== destination || b[b.length - 1].to_code !== origin) return null;
  const q = roundTripQuery(origin, destination, out[0].departure.slice(0, 10), b[0].departure.slice(0, 10), {
    ...r,
    // round_trip_url uses fast-flights defaults for these filters.
    baggage: 'base',
    allow_self_transfer: true,
    exclude_basic_economy: false,
    airlines: [],
    departure_min: '00:00',
    departure_max: '23:59',
    return_min: '00:00',
    return_max: '23:59',
  });
  q.legs.forEach((leg) => {
    leg.earliestDepartureHour = undefined;
    leg.latestDepartureHour = undefined;
  });
  return bookingUrl(q);
}
