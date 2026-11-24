import { describe, expect, it } from 'vitest';
import golden from './__fixtures__/jobs.json';
import { candidateRoutes } from './airports';
import { type Quote, type Trip, buildPlan, parseRequest } from './flexible';
import { JobManager, memoryJobs } from './jobs';
import { type Clock, DEFAULT_SOCS, type Http, type HttpResponse, LiveBlocked, LiveQuotes, LiveUnavailable } from './live';

const TODAY = golden.today;
const scenario = golden.scenarios.weekend;
const served = scenario.served as Record<string, string>;
const request = parseRequest(scenario.spec, TODAY);
const firstTfs = Object.keys(served)[0];

function clock() {
  let t = 1000;
  const sleeps: number[] = [];
  const c: Clock & { sleeps: number[]; advance(s: number): void } = {
    sleeps,
    now: () => t,
    sleep: async (s) => {
      sleeps.push(s);
      t += s;
    },
    advance: (s) => {
      t += s;
    },
  };
  return c;
}

type Handler = (url: string, headers: Record<string, string>, n: number) => HttpResponse | Promise<HttpResponse>;

function http(handler: Handler) {
  const calls: { url: string; headers: Record<string, string> }[] = [];
  const posts: Record<string, string>[] = [];
  const h: Http = {
    async get(url, headers) {
      calls.push({ url, headers });
      return handler(url, headers, calls.length);
    },
    async postForm(_url, fields) {
      posts.push(fields);
      return { status: 200, url: 'https://www.google.com/', text: '', setCookies: ['SOCS=fresh-socs; Domain=.google.com, NID=1; Path=/'] };
    },
  };
  return { h, calls, posts };
}

const ok = (url: string): HttpResponse => {
  const tfs = decodeURIComponent(/[?&]tfs=([^&]+)/.exec(url)![1]);
  return { status: 200, url, text: served[tfs] ?? served[firstTfs] };
};

// The first outbound search of the weekend scenario: VCE+TSF to five airports.
const plan = buildPlan(request, candidateRoutes(request), TODAY);
const day = plan.date_pairs[0][0];
const search = (live: LiveQuotes, fresh = false) =>
  live.searchMany(request.origins, plan.destinations, day, request, false, fresh);

describe('live quotes', () => {
  it('sends the consent cookie and pauses between requests', async () => {
    const c = clock();
    const net = http((url) => ok(url));
    const live = new LiveQuotes(net.h, undefined, c);
    await search(live);
    await live.searchMany(request.origins, plan.destinations, plan.date_pairs[1][0], request);
    expect(net.calls).toHaveLength(2);
    expect(net.calls[0].headers.Cookie).toBe(`SOCS=${DEFAULT_SOCS}`);
    expect(c.sleeps).toHaveLength(1);
    expect(c.sleeps[0]).toBeCloseTo(0.3);
  });

  it('serves repeated searches from the five-minute cache unless fresh', async () => {
    const c = clock();
    const net = http((url) => ok(url));
    const live = new LiveQuotes(net.h, undefined, c);
    const first = await search(live);
    expect(await search(live)).toEqual(first);
    expect(net.calls).toHaveLength(1);
    await search(live, true);
    c.advance(301);
    await search(live);
    expect(net.calls).toHaveLength(3);
  });

  for (const [label, response] of [
    ['429', { status: 429, url: 'https://www.google.com/sorry/index', text: '' }],
    ['403', { status: 403, url: 'https://www.google.com/travel/flights', text: '' }],
    ['sorry page', { status: 200, url: 'https://www.google.com/sorry/index?continue=x', text: '<html></html>' }],
    ['unusual traffic', { status: 200, url: 'https://www.google.com/x', text: 'Our systems have detected unusual traffic' }],
  ] as const) {
    it(`stops on a block (${label}) and does not retry`, async () => {
      const net = http(() => response);
      const live = new LiveQuotes(net.h, undefined, clock());
      for (let i = 0; i < 2; i++) await expect(search(live)).rejects.toBeInstanceOf(LiveBlocked);
      expect(net.calls).toHaveLength(1);
    });
  }

  it('retries a temporary Google error with growing pauses', async () => {
    const c = clock();
    const error =
      "<script class=\"ds:1\">AF_initDataCallback({key: 'ds:1', data:[13,null,[[\"type.googleapis.com/travel.frontend.flights.ErrorResponse\"]]],x:1});</script>";
    const net = http((url, _h, n) => (n <= 2 ? { status: 200, url, text: error } : ok(url)));
    const live = new LiveQuotes(net.h, undefined, c);
    const found = await search(live);
    expect([...found.values()].some((r) => r.quotes.length)).toBe(true);
    expect(c.sleeps.filter((s) => s >= 1)).toEqual([2, 4]);
  });

  it('gives up after network errors, saying so', async () => {
    const net = http(() => {
      throw new Error('offline');
    });
    const live = new LiveQuotes(net.h, undefined, clock());
    await expect(search(live)).rejects.toThrow('Connessione a Google non riuscita');
    // As in Python: three tries on the group, then three on its first route.
    expect(net.calls).toHaveLength(6);
  });

  it('renews the consent once when Google asks for it', async () => {
    const consentPage =
      '<html><a href="https://consent.google.com/">x</a><form action="https://consent.google.com/save"><input type="hidden" name="bl" value="boq_1"><input type="hidden" name="set_eom" value="true"></form>';
    const net = http((url, headers) => {
      if (url.includes('hl=en&gl=IT')) return { status: 200, url: 'https://consent.google.com/m?x', text: consentPage };
      if (!headers.Cookie.includes('fresh-socs')) return { status: 200, url: 'https://consent.google.com/m', text: consentPage };
      return ok(url);
    });
    const live = new LiveQuotes(net.h, undefined, clock());
    await search(live);
    expect(net.posts).toEqual([{ bl: 'boq_1', set_eom: 'true' }]);
    expect(net.calls.at(-1)!.headers.Cookie).toBe('SOCS=fresh-socs');
  });

  it('splits a group search Google cannot answer into single routes', async () => {
    const net = http((url) => {
      const tfs = decodeURIComponent(/[?&]tfs=([^&]+)/.exec(url)![1]);
      return { status: 200, url, text: tfs in served ? '<html>no data</html>' : served[firstTfs] };
    });
    const live = new LiveQuotes(net.h, undefined, clock());
    const found = await search(live);
    expect(net.calls).toHaveLength(1 + request.origins.length * plan.destinations.length);
    expect(found.size).toBe(request.origins.length * plan.destinations.length);
  });
});

describe('jobs', () => {
  const served = scenario.served as Record<string, string>;

  function setup(handler?: Handler) {
    const c = clock();
    const net = http(handler ?? ((url) => ok(url)));
    const store = memoryJobs();
    const manager = new JobManager(store, new LiveQuotes(net.h, undefined, c), c);
    return { manager, net, store };
  }

  it('stops when asked and resumes to the same results', async () => {
    let manager: JobManager;
    let id = '';
    let stopAt = 4;
    const s = setup((url, _h, n) => {
      if (n === stopAt) void manager.cancel(id);
      return ok(url);
    });
    manager = s.manager;
    id = await manager.create(request, plan, TODAY);
    await manager.idle();
    const stopped = await manager.get(id);
    expect(stopped.status).toBe('cancelled');
    expect(s.net.calls).toHaveLength(4);
    stopAt = -1;
    await manager.resume(id);
    await manager.idle();
    const done = await manager.get(id);
    expect(done.status).toBe('complete');
    expect(Object.keys(done.results).sort()).toEqual(Object.keys(scenario.job.results).sort());
    expect(done.round_trips).toEqual(scenario.job.round_trips);
  });

  it('saves a blocked search with its message and refuses a second one meanwhile', async () => {
    const s = setup((url, _h, n) => (n === 3 ? { status: 429, url, text: '' } : ok(url)));
    const id = await s.manager.create(request, plan, TODAY);
    await expect(s.manager.create(request, plan, TODAY)).rejects.toThrow('già in corso');
    await s.manager.idle();
    const job = await s.manager.get(id);
    expect(job.status).toBe('blocked');
    expect(job.issues[0]).toContain('Google ha limitato');
    expect((await s.store.load(id))!.status).toBe('blocked');
  });

  it('a search left running by a closed app is shown as interrupted', async () => {
    const s = setup();
    const id = await s.manager.create(request, plan, TODAY);
    await s.manager.idle();
    const saved = (await s.store.load(id))!;
    await s.store.save({ ...saved, status: 'running' });
    const fresh = new JobManager(s.store, s.manager.live);
    const view = await fresh.view(id);
    expect(view.status).toBe('interrupted');
  });

  it('rechecks a trip: same flights confirmed, missing flights removed', async () => {
    const s = setup();
    const id = await s.manager.create(request, plan, TODAY);
    await s.manager.idle();
    const [first, second] = (await s.manager.view(id)).results;
    const same = setup(() => ({ status: 200, url: 'x', text: pageOf([first.outbound, first.inbound!], 1) }));
    const confirmed = await new JobManager(s.store, same.manager.live).recheck(id, first.id);
    expect(confirmed).toMatchObject({ status: 'confirmed', price_changed: true });
    if (confirmed.status === 'confirmed') expect(confirmed.result.price).toBeCloseTo(first.price + 2);
    const gone = setup(() => ({ status: 200, url: 'x', text: pageOf([], 0, second) }));
    const other = new JobManager(s.store, gone.manager.live);
    expect((await other.recheck(id, second.id)).status).toBe('not_confirmed');
    const view = await other.view(id);
    expect(view.results.map((t) => t.id)).not.toContain(second.id);
    expect(view.result_count).toBe(Object.keys(scenario.job.results).length - 1);
  });
});

/** A Google page listing these quotes again, each `extra` euros dearer. */
function pageOf(quotes: Quote[], extra: number, route?: Trip): string {
  const parts = (iso: string) => [
    [Number(iso.slice(0, 4)), Number(iso.slice(5, 7)), Number(iso.slice(8, 10))],
    [Number(iso.slice(11, 13)), Number(iso.slice(14, 16))],
  ];
  const rows = quotes.map((q) => {
    const legs = q.segments.map((s) => {
      const fl: unknown[] = Array(33).fill(null);
      const [dd, dt] = parts(s.departure);
      const [ad, at] = parts(s.arrival);
      fl[3] = s.from_code;
      fl[6] = s.to_code;
      fl[8] = dt;
      fl[10] = at;
      fl[11] = s.duration_min;
      fl[20] = dd;
      fl[21] = ad;
      fl[22] = [s.airline_code, s.flight_number];
      return fl;
    });
    const detail: unknown[] = Array(25).fill(null);
    detail[0] = q.segments[0].airline_code;
    detail[1] = [q.segments[0].airline_name];
    detail[2] = legs;
    detail[9] = q.duration_min;
    return [detail, [[null, q.price + extra], 'GgUaA0VVUg=='], null, null, null, null, null, null, 'tok'];
  });
  const payload: unknown[] = Array(24).fill(null);
  if (rows.length) {
    payload[2] = [rows];
  } else {
    const s = route!.outbound.segments;
    payload[0] = [null, [[1]], 0, 'r', 's'];
    payload[1] = [[[[[s[0].from_code, 0], 'o']], [[[s[s.length - 1].to_code, 0], 'd']]]];
    payload[7] = [[], [[], []]];
    payload[12] = ['ctx', 0];
    payload[23] = 1;
  }
  return `<script class="ds:1">AF_initDataCallback({data:${JSON.stringify(payload)},sideChannel:{}});</script>`;
}

describe('network errors are typed', () => {
  it('LiveBlocked is a LiveUnavailable', () => {
    expect(new LiveBlocked('x')).toBeInstanceOf(LiveUnavailable);
  });
});
