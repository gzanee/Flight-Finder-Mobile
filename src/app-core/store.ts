/** App-wide state: the engine on this device, the search form, live job views. */
import { activateKeepAwakeAsync, deactivateKeepAwake } from 'expo-keep-awake';
import { useEffect, useState, useSyncExternalStore } from 'react';
import { type Job, JobManager } from '../engine/jobs';
import { LiveQuotes } from '../engine/live';
import { ResultFilters, type Sort } from '../engine/results';
import { type FormState, initialForm } from './form';
import { deviceHttp, fileCookieStore, fileJobStore } from './platform';

export const live = new LiveQuotes(deviceHttp, fileCookieStore());
export const jobs = new JobManager(fileJobStore(), live);

// The screen stays on while a search runs: iOS suspends apps in background.
let awake = false;
jobs.subscribe((job) => {
  const running = job.status === 'running' || job.status === 'cancelling';
  if (running && !awake) {
    awake = true;
    activateKeepAwakeAsync('search').catch(() => undefined);
  } else if (!running && awake && !jobs.running) {
    awake = false;
    deactivateKeepAwake('search').catch(() => undefined);
  }
});

function createStore<T>(initial: T) {
  let value = initial;
  const listeners = new Set<() => void>();
  return {
    get: () => value,
    set(next: T | ((v: T) => T)) {
      value = typeof next === 'function' ? (next as (v: T) => T)(value) : next;
      listeners.forEach((l) => l());
    },
    subscribe(listener: () => void) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
  };
}

export const formStore = createStore<FormState>(initialForm());

export function useForm(): [FormState, (next: FormState | ((f: FormState) => FormState)) => void] {
  const form = useSyncExternalStore(formStore.subscribe, formStore.get);
  return [form, formStore.set];
}

export interface ViewQuery {
  sort: Sort;
  offset: number;
  filters: Record<string, string>;
  focus: string;
}

export type JobView = Awaited<ReturnType<JobManager['view']>>;

/** The job as the results screen shows it, refreshed while the search runs. */
export function useJobView(id: string, query: ViewQuery) {
  const [view, setView] = useState<JobView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [version, setVersion] = useState(0);
  const key = JSON.stringify(query);

  useEffect(() => {
    let last = 0;
    let timer: ReturnType<typeof setTimeout> | null = null;
    // Progress is saved about every second: redraw at most every 1.5 s.
    return jobs.subscribe((job: Job) => {
      if (job.id !== id) return;
      const wait = Math.max(0, 1500 - (Date.now() - last));
      if (timer) return;
      timer = setTimeout(() => {
        timer = null;
        last = Date.now();
        setVersion((v) => v + 1);
      }, wait);
    });
  }, [id]);

  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        const filters = ResultFilters.parse(query.filters);
        const next = await jobs.view(id, query.sort, query.offset, filters, query.focus);
        if (alive) {
          setView(next);
          setError(null);
        }
      } catch (exc) {
        if (alive) setError((exc as Error).message);
      }
    })();
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, key, version]);

  return { view, error, refresh: () => setVersion((v) => v + 1) };
}

/** Saved searches, newest first, with a short summary each. */
export async function savedSearches() {
  const ids = await jobs.store.list();
  const items = [];
  for (const id of ids) {
    try {
      const job = await jobs.get(id);
      items.push({
        id,
        request: job.request,
        status: jobs.activeId === id ? job.status : job.status === 'running' || job.status === 'cancelling' ? 'interrupted' : job.status,
        count: Object.keys(job.results).length,
        created: job.created_at,
        cheapest: Math.min(...Object.values(job.results).map((t) => t.price)),
      });
    } catch {
      // A damaged file is skipped.
    }
  }
  return items.sort((a, b) => (a.created < b.created ? 1 : -1));
}
