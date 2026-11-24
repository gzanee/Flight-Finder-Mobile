/** Italian labels for prices, days and durations, as on the web page. */
import { details } from '../engine/airports';

const euro = new Intl.NumberFormat('it-IT', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 });
const euroCents = new Intl.NumberFormat('it-IT', { style: 'currency', currency: 'EUR', minimumFractionDigits: 2 });

export const money = (n: number) => (n % 1 ? euroCents : euro).format(n);

const WEEKDAYS = ['dom', 'lun', 'mar', 'mer', 'gio', 'ven', 'sab'];
const MONTHS = ['gen', 'feb', 'mar', 'apr', 'mag', 'giu', 'lug', 'ago', 'set', 'ott', 'nov', 'dic'];
const MONTHS_LONG = [
  'gennaio',
  'febbraio',
  'marzo',
  'aprile',
  'maggio',
  'giugno',
  'luglio',
  'agosto',
  'settembre',
  'ottobre',
  'novembre',
  'dicembre',
];
export const DAY_LETTERS = ['Lun', 'Mar', 'Mer', 'Gio', 'Ven', 'Sab', 'Dom'];

function parts(iso: string) {
  const y = Number(iso.slice(0, 4));
  const m = Number(iso.slice(5, 7));
  const d = Number(iso.slice(8, 10));
  return { y, m, d, wd: new Date(Date.UTC(y, m - 1, d)).getUTCDay() };
}

/** "ven 12 dic" from the local date of an ISO string. */
export function shortDay(iso: string): string {
  const { m, d, wd } = parts(iso);
  return `${WEEKDAYS[wd]} ${d} ${MONTHS[m - 1]}`;
}

export function longDay(iso: string): string {
  const { y, m, d, wd } = parts(iso);
  return `${WEEKDAYS[wd]} ${d} ${MONTHS_LONG[m - 1]} ${y}`;
}

export const monthTitle = (y: number, m: number) => `${MONTHS_LONG[m - 1]} ${y}`;

export const localTime = (iso: string) => iso.slice(11, 16);

export const duration = (minutes: number) => `${Math.floor(minutes / 60)}h ${String(minutes % 60).padStart(2, '0')}m`;

export const countLabel = (n: number, single: string, plural: string) => `${n} ${n === 1 ? single : plural}`;

export const city = (code: string) => details(code).city;

export const airportName = (code: string) => details(code).name;

/** Flag emoji from an ISO country code (no image files needed). */
export function flag(code: string): string {
  const country = details(code).country;
  if (!/^[a-z]{2}$/.test(country)) return '';
  return String.fromCodePoint(...[...country.toUpperCase()].map((c) => 0x1f1e6 + c.charCodeAt(0) - 65));
}

export function countryFlag(country: string): string {
  if (!/^[a-z]{2}$/i.test(country)) return '';
  return String.fromCodePoint(...[...country.toUpperCase()].map((c) => 0x1f1e6 + c.charCodeAt(0) - 65));
}

/** Only links to Google Flights are opened. */
export function safeLink(url: string | null | undefined): string | null {
  if (!url) return null;
  return /^https:\/\/www\.google\.com\//.test(url) ? url : null;
}

export function remaining(eta: number | null): string {
  if (eta == null) return 'qualche minuto';
  const minutes = Math.ceil(eta / 60);
  if (minutes <= 1) return 'meno di un minuto';
  if (minutes < 90) return `circa ${minutes} minuti`;
  return `circa ${Math.round(minutes / 6) / 10} ore`.replace('.', ',');
}
