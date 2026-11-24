/**
 * Web preview only (npx expo start --web): browsers cannot call Google Flights
 * (CORS), so searches run against a deterministic demo Google built here.
 * The phone builds use platform.ts and the real connection.
 */
import { b64decode } from '../engine/decode';
import type { JobStore } from '../engine/jobs';
import { memoryJobs } from '../engine/jobs';
import { type CookieStore, type Http, memoryCookies } from '../engine/live';

export const USER_AGENT = 'web-preview';

export function fileJobStore(): JobStore {
  return memoryJobs();
}

export function fileCookieStore(): CookieStore {
  return memoryCookies();
}

interface Leg {
  date: string;
  from: string[];
  to: string[];
}

function readVarint(b: number[], i: number): [number, number] {
  let n = 0;
  let shift = 0;
  for (;;) {
    const byte = b[i++];
    n += (byte & 0x7f) * 2 ** shift;
    if (!(byte & 0x80)) return [n, i];
    shift += 7;
  }
}

function fields(b: number[]): [number, number, number[] | number][] {
  const out: [number, number, number[] | number][] = [];
  let i = 0;
  while (i < b.length) {
    const [tag, j] = readVarint(b, i);
    i = j;
    const wire = tag & 7;
    if (wire === 0) {
      const [v, k] = readVarint(b, i);
      out.push([tag >> 3, wire, v]);
      i = k;
    } else {
      const [len, k] = readVarint(b, i);
      out.push([tag >> 3, wire, b.slice(k, k + len)]);
      i = k + len;
    }
  }
  return out;
}

const text = (b: number[]) => String.fromCharCode(...b);

function legsOf(tfs: string): { legs: Leg[]; trip: number } {
  const legs: Leg[] = [];
  let trip = 2;
  for (const [field, , value] of fields(b64decode(tfs))) {
    if (field === 19) trip = value as number;
    if (field !== 3) continue;
    const leg: Leg = { date: '', from: [], to: [] };
    for (const [f, , v] of fields(value as number[])) {
      if (f === 2) leg.date = text(v as number[]);
      if (f === 13 || f === 14) {
        const code = text((fields(v as number[]).find(([n]) => n === 2)?.[2] as number[]) ?? []);
        (f === 13 ? leg.from : leg.to).push(code);
      }
    }
    legs.push(leg);
  }
  return { legs, trip };
}

function rng(seed: string) {
  let h = 2166136261;
  for (const ch of seed) h = Math.imul(h ^ ch.charCodeAt(0), 16777619);
  return () => {
    h = Math.imul(h ^ (h >>> 15), 2246822507);
    h = Math.imul(h ^ (h >>> 13), 3266489909);
    return ((h ^= h >>> 16) >>> 0) / 4294967296;
  };
}

const AIRLINES = [
  ['FR', 'Ryanair'],
  ['U2', 'easyJet'],
  ['W6', 'Wizz Air'],
  ['VY', 'Vueling'],
  ['AZ', 'ITA Airways'],
];

function page(tfs: string): string {
  const { legs, trip } = legsOf(tfs);
  const leg = legs[0];
  const [y, m, d] = leg.date.split('-').map(Number);
  const rows: unknown[] = [];
  for (const from of leg.from) {
    for (const to of leg.to) {
      if (from === to) continue;
      const rand = rng(`${tfs}|${from}|${to}`);
      const n = Math.floor(rand() * 4);
      for (let k = 0; k < n; k++) {
        const [code, name] = AIRLINES[Math.floor(rand() * AIRLINES.length)];
        const dep = 6 + Math.floor(rand() * 16);
        const minute = [0, 10, 25, 40, 55][Math.floor(rand() * 5)];
        const dur = 70 + Math.floor(rand() * 120);
        const arrTotal = dep * 60 + minute + dur;
        const fl: unknown[] = Array(33).fill(null);
        fl[3] = from;
        fl[6] = to;
        fl[8] = [dep, minute];
        fl[10] = [Math.floor(arrTotal / 60) % 24, arrTotal % 60];
        fl[11] = dur;
        fl[20] = [y, m, d];
        const next = new Date(Date.UTC(y, m - 1, d + Math.floor(arrTotal / 1440)));
        fl[21] = [next.getUTCFullYear(), next.getUTCMonth() + 1, next.getUTCDate()];
        fl[22] = [code, String(1000 + Math.floor(rand() * 8000))];
        const detail: unknown[] = Array(25).fill(null);
        detail[0] = code;
        detail[1] = [name];
        detail[2] = [fl];
        detail[9] = dur;
        const price = Math.round((19 + rand() * 140) * (trip === 1 ? 1.7 : 1));
        rows.push([detail, [[null, price], 'GgUaA0VVUg=='], null, null, null, null, null, null, 'demo']);
      }
    }
  }
  const payload: unknown[] = Array(24).fill(null);
  if (rows.length) {
    payload[2] = [rows];
    payload[7] = [null, [null, AIRLINES]];
  } else {
    payload[0] = [null, [[1]], 0, 'r', 's'];
    payload[1] = [[leg.from.map((c) => [[c, 0], 'o']), leg.to.map((c) => [[c, 0], 'd'])]];
    payload[7] = [[], [[], []]];
    payload[12] = ['ctx', 0];
    payload[23] = 1;
  }
  return `<script class="ds:1">AF_initDataCallback({data:${JSON.stringify(payload)},sideChannel:{}});</script>`;
}

export const deviceHttp: Http = {
  async get(url) {
    await new Promise((r) => setTimeout(r, 120));
    const tfs = decodeURIComponent(/[?&]tfs=([^&]+)/.exec(url)?.[1] ?? '');
    return { status: 200, url, text: tfs ? page(tfs) : '' };
  },
  async postForm(url) {
    return { status: 200, url, text: '' };
  },
};
