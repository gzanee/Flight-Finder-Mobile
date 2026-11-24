/** Device adapters for the engine: files for saved searches, fetch for Google. */
import { Directory, File, Paths } from 'expo-file-system';
import type { Job, JobStore } from '../engine/jobs';
import type { CookieStore, Http, HttpResponse } from '../engine/live';

// The same browser identity as the curl test that Google answered from a home connection.
export const USER_AGENT =
  'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1';
const TIMEOUT_MS = 25000;

function folder(name: string): Directory {
  const dir = new Directory(Paths.document, name);
  if (!dir.exists) dir.create({ intermediates: true });
  return dir;
}

export function fileJobStore(): JobStore {
  const dir = () => folder('searches');
  const file = (id: string) => new File(dir(), `${id}.json`);
  return {
    async load(id) {
      const f = file(id);
      if (!f.exists) return null;
      return JSON.parse(await f.text()) as Job;
    },
    async save(job) {
      // Write a new file and swap it in, so a crash never leaves half a search.
      const tmp = new File(dir(), `${job.id}.json.tmp`);
      await tmp.write(JSON.stringify(job));
      await tmp.move(file(job.id), { overwrite: true });
    },
    async list() {
      return dir()
        .list()
        .filter((entry): entry is File => entry instanceof File && entry.name.endsWith('.json'))
        .map((entry) => entry.name.slice(0, -5));
    },
    async remove(id) {
      const f = file(id);
      if (f.exists) f.delete();
    },
  };
}

export function fileCookieStore(): CookieStore {
  const file = () => new File(folder('settings'), 'google_consent.json');
  return {
    async load() {
      const f = file();
      if (!f.exists) return null;
      const blob = JSON.parse(await f.text());
      // Renewed weekly, like the Python engine.
      if (Date.now() / 1000 - (blob.ts ?? 0) > 7 * 24 * 3600) return null;
      return blob.cookies ?? null;
    },
    async save(cookies) {
      await file().write(JSON.stringify({ ts: Date.now() / 1000, cookies }));
    },
  };
}

async function request(url: string, init: RequestInit): Promise<HttpResponse> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    const response = await fetch(url, {
      ...init,
      // Cookies are sent by hand: the system cookie jar must not add others.
      credentials: 'omit',
      signal: controller.signal,
      headers: { 'User-Agent': USER_AGENT, ...(init.headers as Record<string, string>) },
    });
    const setCookie = response.headers.get('set-cookie');
    return {
      status: response.status,
      url: response.url || url,
      text: await response.text(),
      setCookies: setCookie ? [setCookie] : [],
    };
  } finally {
    clearTimeout(timer);
  }
}

export const deviceHttp: Http = {
  get: (url, headers) => request(url, { method: 'GET', headers }),
  postForm: (url, fields, headers) =>
    request(url, {
      method: 'POST',
      headers: { ...headers, 'Content-Type': 'application/x-www-form-urlencoded' },
      body: Object.entries(fields)
        .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
        .join('&'),
    }),
};
