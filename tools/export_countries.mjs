// Nomi italiani dei paesi del catalogo aeroporti (Intl di Node, ICU completo).
// Uso: node tools/export_countries.mjs
import { readFileSync, writeFileSync } from 'node:fs';
const rows = JSON.parse(readFileSync(new URL('../src/engine/data/airports.json', import.meta.url)));
const names = new Intl.DisplayNames(['it'], { type: 'region' });
const out = {};
for (const row of Object.values(rows)) {
  const c = row[3];
  if (/^[A-Z]{2}$/.test(c) && !(c.toLowerCase() in out)) out[c.toLowerCase()] = names.of(c);
}
writeFileSync(new URL('../src/engine/data/countries_it.json', import.meta.url), JSON.stringify(out, Object.keys(out).sort(), 0));
console.log(Object.keys(out).length, 'paesi', out.es, out.gb, out.us);
