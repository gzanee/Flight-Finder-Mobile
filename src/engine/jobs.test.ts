import { describe, expect, it } from 'vitest';
import golden from './__fixtures__/jobs.json';
import { candidateRoutes } from './airports';
import { buildPlan, parseRequest } from './flexible';
import { type Job, JobManager, memoryJobs } from './jobs';
import { type Clock, type Http, LiveQuotes } from './live';
import { ResultFilters } from './results';

type Scenario = (typeof golden.scenarios)[keyof typeof golden.scenarios];

function fakeGoogle(served: Record<string, string>) {
  const asked: string[] = [];
  const http: Http = {
    async get(url) {
      const tfs = decodeURIComponent(/[?&]tfs=([^&]+)/.exec(url)![1]);
      asked.push(tfs);
      if (!(tfs in served)) throw new Error(`Richiesta non fatta dal Python: ${url}`);
      return { status: 200, url, text: served[tfs] };
    },
    async postForm() {
      throw new Error('no consent flow in tests');
    },
  };
  return { http, asked };
}

function fakeClock(): Clock {
  let t = Date.now() / 1000;
  return {
    now: () => (t += 0.001),
    sleep: async (s) => {
      t += s;
    },
  };
}

const strip = (value: unknown): unknown =>
  JSON.parse(JSON.stringify(value), (key, v) =>
    ['checked_at', 'needs_recheck', 'eta_seconds', 'updated_at', 'created_at', 'id_job'].includes(key) ? undefined : v,
  );

const VIEW_ARGS: Record<string, [string, Record<string, string>]> = {
  price: ['price', {}],
  useful: ['useful', {}],
  duration: ['duration', {}],
  departure_filtered: ['departure', { max_price: '150' }],
  home_before: ['price', { home_before: '21:00' }],
  airline: ['price', { airline: 'FR' }],
  min_hours: ['useful', { min_hours: '45' }],
  places: ['price', { origin: 'VCE', destination: 'BCN,BER,STN,LTN', exclude: 'LTN' }],
};

describe('complete searches, identical to the Python engine', () => {
  for (const [name, s] of Object.entries(golden.scenarios) as [string, Scenario][]) {
    it(name, async () => {
      const request = parseRequest(s.spec, golden.today);
      const plan = buildPlan(request, candidateRoutes(request), golden.today);
      expect(plan).toEqual(s.plan);

      const google = fakeGoogle(s.served as Record<string, string>);
      const clock = fakeClock();
      const store = memoryJobs();
      const manager = new JobManager(store, new LiveQuotes(google.http, undefined, clock), clock);
      const id = await manager.create(request, plan, golden.today);
      await manager.idle();
      const job = (await manager.get(id)) as Job;
      const expected = s.job as unknown as Job;

      expect(google.asked.length).toBe(Object.keys(s.served).length);
      expect(job.status).toBe(expected.status);
      expect(job.issues).toEqual(expected.issues);
      for (const field of [
        'completed',
        'total',
        'requests',
        'return_days',
        'return_requests',
        'unverified',
        'empty_searches',
        'cursor',
        'pass',
        'outbound',
      ] as const) {
        expect([field, job[field]]).toEqual([field, expected[field]]);
      }
      expect(Object.keys(job.results).sort()).toEqual(Object.keys(expected.results).sort());
      expect(strip(job.results)).toEqual(strip(expected.results));
      expect(job.round_trips ?? {}).toEqual(expected.round_trips ?? {});

      for (const [key, [sort, filters]] of Object.entries(VIEW_ARGS)) {
        const view = await manager.view(id, sort, 0, ResultFilters.parse(filters));
        const want = (s.views as Record<string, Record<string, unknown>>)[key];
        for (const field of [
          'result_count',
          'filtered_count',
          'facets',
          'groups',
          'recommendations',
          'calendar',
          'results',
          'offset',
          'has_more',
          'focused_id',
          'airports',
        ]) {
          expect([key, field, strip((view as Record<string, unknown>)[field])]).toEqual([
            key,
            field,
            strip(want[field]),
          ]);
        }
      }
    });
  }
});
