/**
 * Resumable search jobs on the device: port of flightcore/flexible_jobs.py.
 * Progress is saved; a block stops the search; no availability is invented.
 */
import { details as airportDetails, type AirportDetails } from './airports';
import {
  type FlexibleRequest,
  type Plan,
  type Quote,
  type Route,
  type Schedule,
  type Trip,
  combine,
  fingerprint,
  matches,
  returnRoutes,
  searchPasses,
  singleTrip,
} from './flexible';
import { type Clock, LiveBlocked, type LiveQuotes, LiveUnavailable, QUOTE_TTL, type RouteResult, realClock, roundTripUrl, secondsPerSearch } from './live';
import { ResultFilters, browseResults, compareKeys, sortKey } from './results';
import { today as deviceToday, utcNow } from './time';

export const ENGINE = 3;
const SAVE_INTERVAL = 1.0;
const PACE_SAMPLE = 3;
export const ROUND_TRIP_CHECKS = 150;
const SHOWN_AIRPORTS = 3;

export type JobStatus = 'running' | 'cancelling' | 'cancelled' | 'complete' | 'blocked' | 'interrupted';

export type Recheck =
  | { status: 'confirmed'; result: Trip; price_changed: boolean }
  | { status: 'not_confirmed' | 'outside_filters'; message: string };

export interface Job {
  id: string;
  request: FlexibleRequest;
  plan: Plan;
  status: JobStatus;
  engine: number;
  completed: number;
  total: number;
  cursor: [number, number, number];
  pass: number;
  outbound: Record<string, Quote[]>;
  requests: number;
  return_days: number;
  return_requests: number;
  eta_seconds: number | null;
  results: Record<string, Trip>;
  rechecks: Record<string, Recheck>;
  round_trips?: Record<string, Record<string, number>>;
  unverified: number;
  empty_searches: number;
  issues: string[];
  created_at: string;
  updated_at: string;
  current: string | null;
  /** Date of the search, for the horizon of Google's sales (device "today"). */
  today?: string;
}

export interface JobStore {
  load(id: string): Promise<Job | null>;
  save(job: Job): Promise<void>;
  list(): Promise<string[]>;
  remove(id: string): Promise<void>;
}

export const memoryJobs = (): JobStore & { data: Map<string, string> } => {
  const data = new Map<string, string>();
  return {
    data,
    load: async (id) => (data.has(id) ? JSON.parse(data.get(id) as string) : null),
    save: async (job) => {
      data.set(job.id, JSON.stringify(job));
    },
    list: async () => [...data.keys()],
    remove: async (id) => {
      data.delete(id);
    },
  };
};

export function airportList(codes: string[]): string {
  if (codes.length <= SHOWN_AIRPORTS) return codes.join(', ');
  return `${codes.slice(0, SHOWN_AIRPORTS - 1).join(', ')} e altri ${codes.length - SHOWN_AIRPORTS + 1}`;
}

export function resultAirports(request: FlexibleRequest, results: Trip[]): Record<string, AirportDetails> {
  const codes = new Set(request.origins);
  for (const trip of results) {
    for (const leg of [trip.outbound, trip.inbound]) {
      if (leg) for (const s of leg.segments) codes.add(s.from_code).add(s.to_code);
    }
  }
  return Object.fromEntries([...codes].sort().map((c) => [c, airportDetails(c)]));
}

function newId(): string {
  let id = '';
  for (let i = 0; i < 32; i++) id += Math.floor(Math.random() * 16).toString(16);
  return id;
}

interface Pace {
  started: number;
  requests: number;
  later: number;
  done: number;
  label: string;
}

export type JobListener = (job: Job) => void;

export class JobManager {
  private active: Job | null = null;
  private worker: Promise<void> | null = null;
  private savedAt = 0;
  private saveCost = 0;
  private listeners = new Set<JobListener>();

  constructor(
    readonly store: JobStore,
    readonly live: LiveQuotes,
    private clock: Clock = realClock,
  ) {}

  /** Called after every saved step, for a live progress view. */
  subscribe(listener: JobListener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  get running(): boolean {
    return this.worker !== null;
  }

  get activeId(): string | null {
    return this.worker ? (this.active?.id ?? null) : null;
  }

  async get(id: string): Promise<Job> {
    if (this.active?.id === id) return this.active;
    const job = await this.store.load(id);
    if (!job) throw new Error('Ricerca o risultato non trovato.');
    return job;
  }

  private async save(job: Job) {
    await this.store.save(job);
    for (const listener of this.listeners) listener(job);
  }

  async create(request: FlexibleRequest, plan: Plan, today = deviceToday()): Promise<string> {
    if (this.worker) throw new Error('Una ricerca è già in corso: fermala prima di avviarne un altra.');
    const job: Job = {
      id: newId(),
      request,
      plan,
      status: 'running',
      engine: ENGINE,
      completed: 0,
      total: plan.searches_upper_bound,
      cursor: [0, 0, 0],
      pass: 0,
      outbound: {},
      requests: 0,
      return_days: 0,
      return_requests: 0,
      eta_seconds: null,
      results: {},
      rechecks: {},
      unverified: 0,
      empty_searches: 0,
      issues: [],
      created_at: utcNow(),
      updated_at: utcNow(),
      current: null,
      today,
    };
    await this.save(job);
    this.launch(job);
    return job.id;
  }

  private launch(job: Job) {
    this.active = job;
    this.worker = this.run(job).finally(() => {
      this.worker = null;
    });
  }

  /** Resolves when the running search, if any, has stopped and been saved. */
  async idle(): Promise<void> {
    await this.worker;
  }

  async resume(id: string) {
    if (this.worker) throw new Error('Attendi che la ricerca attiva si fermi.');
    const job = await this.get(id);
    if (job.status === 'complete') throw new Error('Ricerca già completata. Avviane una nuova per prezzi aggiornati.');
    job.status = 'running';
    job.issues = [];
    await this.save(job);
    this.launch(job);
  }

  async cancel(id: string) {
    const job = await this.get(id);
    if (job.status === 'running') {
      job.status = 'cancelling';
      if (this.active !== job) await this.save(job);
    }
  }

  private stopped(job: Job) {
    return job.status === 'cancelling';
  }

  private async run(job: Job) {
    const request = job.request;
    const passes = searchPasses(request, job.plan.destinations, job.today ?? deviceToday());
    if (job.engine !== ENGINE) {
      Object.assign(job, { engine: ENGINE, completed: 0, cursor: [0, 0, 0], outbound: {}, requests: 0 });
      Object.assign(job, { pass: 0, return_days: 0, return_requests: 0 });
    }
    job.total = passes.reduce((n, s) => n + s.total, 0);
    this.savedAt = 0;
    this.saveCost = 0;
    const pace: Pace = { started: this.clock.now(), requests: 0, later: 0, done: 0, label: '' };
    let status: JobStatus = 'complete';
    try {
      for (let number = job.pass; number < passes.length; number++) {
        pace.later = passes
          .slice(number + 1)
          .reduce((n, p) => n + p.total * secondsPerSearch(p.request, this.live.requestInterval), 0);
        pace.done = passes.slice(0, number).reduce((n, p) => n + p.total, 0);
        pace.label = passes.length > 1 ? ['Voli diretti · ', 'Con scalo · '][number] : '';
        if ((await this.search(job, request, passes[number], pace)) === 'cancelled') {
          status = 'cancelled';
          break;
        }
        Object.assign(job, { pass: number + 1, cursor: [0, 0, 0], outbound: {}, return_days: 0, return_requests: 0 });
      }
      if (status === 'complete' && !(await this.compareRoundTrips(job, request))) status = 'cancelled';
      const last = passes[passes.length - 1];
      if (status === 'complete' && last.unsoldFrom) {
        job.issues = [
          `Google vende i voli fino al ${last.horizon.split('-').reverse().join('/')}: ` +
            'le date successive non sono state cercate.',
        ];
      }
    } catch (exc) {
      if (exc instanceof LiveBlocked) {
        status = 'blocked';
        job.issues = [exc.message];
      } else {
        status = 'interrupted';
        job.issues = [
          exc instanceof LiveUnavailable ? exc.message : 'Ricerca interrotta da un errore; il progresso è salvato.',
        ];
      }
    } finally {
      // A stop requested meanwhile wins, as in Python.
      job.status = job.status === 'cancelling' ? 'cancelled' : status;
      job.updated_at = utcNow();
      if (job.status === 'complete') job.eta_seconds = 0;
      await this.save(job);
    }
  }

  /** Run one schedule from the saved cursor (day, stage, batch); return the final status. */
  private async search(job: Job, request: FlexibleRequest, schedule: Schedule, pace: Pace) {
    let [start, stage, batch] = job.cursor;
    const searched = schedule.request;
    const { done, label: prefix } = pace;
    for (let index = start; index < schedule.days.length; index++) {
      const day = schedule.days[index];
      const base = done + schedule.unitsBefore(index);
      const outs = schedule.outDays.has(day) ? schedule.outBatches.length : 0;
      if (stage === 0) {
        for (let number = batch; number < outs; number++) {
          const [origins, destinations] = schedule.outBatches[number];
          const label = `${airportList(origins)} → ${airportList(destinations)} · ${day}`;
          if (!(await this.begin(job, prefix + label))) return 'cancelled';
          const found = await this.live.searchMany(origins, destinations, day, searched);
          this.keepOutbound(job, request, day, found);
          job.cursor = [index, 0, number + 1];
          job.completed = base + number + 1;
          await this.advance(job, schedule, pace);
        }
        stage = 1;
        batch = 0;
      }
      if (schedule.outsFor.has(day)) {
        const wanted = this.wantedReturns(job, request, schedule, day);
        const batches = schedule.returnBatches([...wanted.keys()].map((k) => k.split('|') as Route));
        for (let number = batch; number < batches.length; number++) {
          const [aways, homes] = batches[number];
          const label = `Ritorno: ${airportList(aways)} → ${airportList(homes)} · ${day}`;
          if (!(await this.begin(job, prefix + label))) return 'cancelled';
          const found = await this.live.searchMany(aways, homes, day, searched, true);
          this.combineReturns(job, request, day, found, wanted);
          job.cursor = [index, 1, number + 1];
          job.completed = base + outs + Math.min(number + 1, schedule.backBound);
          job.return_requests += 1;
          await this.advance(job, schedule, pace);
        }
        job.return_days += 1;
      }
      for (const key of Object.keys(job.outbound)) {
        // Every return day of that outbound has been priced.
        if ((schedule.lastBack.get(key.slice(-10)) ?? '') <= day) delete job.outbound[key];
      }
      stage = 0;
      batch = 0;
      job.cursor = [index + 1, 0, 0];
      job.completed = done + schedule.unitsBefore(index + 1);
    }
    start = 0;
    return 'complete';
  }

  static roundTripKey(trip: Trip): string | null {
    const out = trip.outbound.segments;
    const back = trip.inbound?.segments;
    if (!back) return null;
    const origin = out[0].from_code;
    const destination = out[out.length - 1].to_code;
    if (back[0].from_code !== destination || back[back.length - 1].to_code !== origin) return null;
    return `${origin}|${destination}|${out[0].departure.slice(0, 10)}|${back[0].departure.slice(0, 10)}`;
  }

  /** Compare trips with Google's single-ticket return fares; false if stopped. */
  private async compareRoundTrips(job: Job, request: FlexibleRequest): Promise<boolean> {
    const price = new Map<string, number>();
    for (const trip of Object.values(job.results)) {
      const key = JobManager.roundTripKey(trip);
      if (key) price.set(key, Math.min(trip.price, price.get(key) ?? trip.price));
    }
    const byPrice = [...price.keys()].sort((a, b) => compareKeys([price.get(a)!, a], [price.get(b)!, b]));
    const first = new Map<string, string>();
    for (const key of byPrice) if (!first.has(key.slice(0, 7))) first.set(key.slice(0, 7), key);
    const ordered = [...first.values(), ...byPrice.filter((key) => first.get(key.slice(0, 7)) !== key)];
    job.round_trips ??= {};
    const fares = job.round_trips;
    const keys = ordered.slice(0, ROUND_TRIP_CHECKS).filter((key) => !(key in fares));
    const base = job.completed;
    job.total = base + keys.length;
    for (const [number, key] of keys.entries()) {
      const [origin, destination, out, back] = key.split('|');
      if (!(await this.begin(job, `Confronto con biglietto A/R: ${origin} → ${destination} · ${out}`))) return false;
      const found = await this.live.roundTrip(origin, destination, out, back, request);
      if (found !== null) fares[key] = found;
      job.completed = base + number + 1;
      job.eta_seconds = keys.length - number - 1;
      await this.progress(job);
    }
    return true;
  }

  private async begin(job: Job, label: string) {
    if (this.stopped(job)) return false;
    job.current = label;
    await this.progress(job);
    return true;
  }

  private async advance(job: Job, schedule: Schedule, pace: Pace) {
    job.requests += 1;
    pace.requests += 1;
    const measured = (this.clock.now() - pace.started) / pace.requests;
    const fallback = secondsPerSearch(schedule.request, this.live.requestInterval);
    const seconds = pace.requests >= PACE_SAMPLE ? measured : fallback;
    const share =
      job.return_days && schedule.backBound ? job.return_requests / (job.return_days * schedule.backBound) : 1;
    job.eta_seconds = Math.round(schedule.remaining(job.cursor, share) * seconds + pace.later);
    await this.progress(job);
  }

  private keepOutbound(job: Job, request: FlexibleRequest, day: string, found: Map<string, RouteResult>) {
    for (const [pair, result] of found) {
      const [origin, destination] = pair.split('|');
      job.unverified += result.unverified;
      job.empty_searches += Number(result.empty);
      const outbound = result.quotes.filter(
        (q) => matches(q, request, origin, destination, day) && q.price <= request.budget,
      );
      if (request.trip_type === 'one_way') {
        for (const quote of outbound) {
          const trip = singleTrip(quote, request) as Trip;
          job.results[trip.id] = trip;
        }
      } else if (outbound.length) {
        job.outbound[`${origin}|${destination}|${day}`] = outbound;
      }
    }
  }

  private wantedReturns(job: Job, request: FlexibleRequest, schedule: Schedule, day: string) {
    const wanted = new Map<string, Quote[]>();
    for (const out of schedule.outsFor.get(day) ?? []) {
      for (const [origin, destination] of schedule.routes) {
        const outbound = job.outbound[`${origin}|${destination}|${out}`];
        if (!outbound) continue;
        for (const route of returnRoutes(request, origin, destination)) {
          const key = route.join('|');
          if (!wanted.has(key)) wanted.set(key, []);
          wanted.get(key)!.push(...outbound);
        }
      }
    }
    return wanted;
  }

  private combineReturns(
    job: Job,
    request: FlexibleRequest,
    day: string,
    found: Map<string, RouteResult>,
    wanted: Map<string, Quote[]>,
  ) {
    for (const [pair, result] of found) {
      const [away, home] = pair.split('|');
      job.unverified += result.unverified;
      job.empty_searches += Number(result.empty);
      const inbound = result.quotes.filter((q) => matches(q, request, away, home, day, true));
      for (const a of wanted.get(pair) ?? []) {
        for (const b of inbound) {
          const trip = combine(a, b, request);
          if (trip) job.results[trip.id] = trip;
        }
      }
    }
  }

  private async progress(job: Job) {
    // Never spend more than about a fifth of the time saving.
    if (this.clock.now() - this.savedAt < Math.max(SAVE_INTERVAL, 5 * this.saveCost)) return;
    const started = this.clock.now();
    job.updated_at = utcNow();
    if (job.status === 'cancelling') return;
    await this.save(job);
    this.savedAt = this.clock.now();
    this.saveCost = this.savedAt - started;
  }

  /** The job as the results screen shows it: page of trips, filters, groups, suggestions. */
  async view(id: string, sort = 'price', offset = 0, filters = new ResultFilters(), focus = '') {
    const job = structuredClone(await this.get(id));
    if ((job.status === 'running' || job.status === 'cancelling') && this.activeId !== id) {
      job.status = 'interrupted';
      job.issues = ['App chiusa durante la ricerca: premi Riprendi per continuare.'];
    }
    const all = JobManager.currentResults(job, this.clock);
    const browsed = browseResults(all, sort, offset, filters, focus);
    for (const item of browsed.results) item.round_trip_url = roundTripUrl(item, job.request);
    const { outbound: _o, cursor: _c, round_trips: _r, results: _res, rechecks: _rc, ...rest } = job;
    return { ...rest, airports: resultAirports(job.request, all), ...browsed };
  }

  /** Saved trips with rechecks applied and freshness marked. */
  static currentResults(job: Job, clock: Clock = realClock): Trip[] {
    const results = { ...job.results };
    for (const [id, check] of Object.entries(job.rechecks ?? {})) {
      if (check.status === 'confirmed') results[id] = check.result;
      else delete results[id];
    }
    const now = clock.now();
    const fares = job.round_trips ?? {};
    return Object.values(results).map((item) => {
      const age = now - Date.parse(item.checked_at) / 1000;
      const trip: Trip = { ...item, needs_recheck: age > QUOTE_TTL };
      const fare = fares[JobManager.roundTripKey(item) ?? '']?.[item.id.slice(0, 24)];
      if (fare && fare < item.price) trip.round_trip_from = fare;
      return trip;
    });
  }

  /** Filtered trips, cheapest first, for sharing. No provider calls. */
  async export(id: string, filters = new ResultFilters()) {
    const job = await this.get(id);
    const trips = JobManager.currentResults(job, this.clock)
      .filter((t) => filters.matches(t))
      .sort((a, b) => compareKeys(sortKey(a, 'price'), sortKey(b, 'price')));
    return { request: job.request, trips };
  }

  async recheck(id: string, resultId: string): Promise<Recheck> {
    const job = await this.get(id);
    let pair = job.results[resultId];
    if (!pair) throw new Error('Ricerca o risultato non trovato.');
    const previous = job.rechecks?.[resultId];
    if (previous?.status === 'confirmed') pair = previous.result;
    const request = job.request;
    const selected: Quote[] = [];
    for (const key of ['outbound', 'inbound'] as const) {
      const quote = pair[key];
      if (!quote) continue;
      const origin = quote.segments[0].from_code;
      const destination = quote.segments[quote.segments.length - 1].to_code;
      const day = quote.segments[0].departure.slice(0, 10);
      const inbound = key === 'inbound';
      const result = await this.live.search(origin, destination, day, request, inbound, true);
      const match = result.quotes.find(
        (q) => fingerprint(q) === fingerprint(quote) && matches(q, request, origin, destination, day, inbound),
      );
      if (!match) {
        return this.storeRecheck(id, resultId, {
          status: 'not_confirmed',
          message:
            'Gli stessi voli non sono stati confermati con questi filtri. Il risultato precedente non è più valido.',
        });
      }
      selected.push(match);
    }
    const updated =
      request.trip_type === 'one_way' ? singleTrip(selected[0], request) : combine(selected[0], selected[1], request);
    if (!updated) {
      return this.storeRecheck(id, resultId, {
        status: 'outside_filters',
        message: 'Il prezzo o gli orari non rispettano più i filtri.',
      });
    }
    return this.storeRecheck(id, resultId, {
      status: 'confirmed',
      result: updated,
      price_changed: updated.price !== pair.price,
    });
  }

  private async storeRecheck(id: string, resultId: string, check: Recheck) {
    const job = await this.get(id);
    job.rechecks ??= {};
    job.rechecks[resultId] = check;
    job.updated_at = utcNow();
    await this.save(job);
    return check;
  }

  async remove(id: string) {
    if (this.activeId === id) throw new Error('Ferma la ricerca prima di eliminarla.');
    await this.store.remove(id);
  }
}
