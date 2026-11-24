import { describe, expect, it } from 'vitest';
import cases from './__fixtures__/decode.json';
import { decode } from './decode';
import { parseRequest } from './flexible';

const request = parseRequest(
  {
    origins: ['VCE'],
    destinations: ['BCN'],
    trip_type: 'one_way',
    date_mode: 'fixed',
    start_date: '2026-12-24',
    end_date: '2026-12-24',
    adults: 2,
    baggage: 'cabin',
  },
  '2026-11-24',
);

describe('decoder, identical to the Python engine', () => {
  for (const c of cases) {
    it(c.name, () => {
      const expected = c.expected_route as [string[], string[]] | null;
      let result: unknown;
      try {
        const [quotes, rejected] = decode(c.html, request, 'https://example/url', expected);
        result = { quotes: quotes.map(({ checked_at, ...q }) => q), rejected };
      } catch (exc) {
        result = { error: (exc as Error).name };
      }
      expect(result).toEqual(c.result);
    });
  }
});

describe('real Google page (VCE-BCN, captured from a home connection)', () => {
  it('decodes like the Python engine', async () => {
    const { gunzipSync } = await import('node:zlib');
    const { readFileSync } = await import('node:fs');
    const html = gunzipSync(readFileSync(`${__dirname}/__fixtures__/google_real_vce_bcn.html.gz`)).toString('utf8');
    const want = JSON.parse(readFileSync(`${__dirname}/__fixtures__/google_real_vce_bcn.json`, 'utf8'));
    const req = parseRequest(
      { origins: ['VCE'], destinations: ['BCN'], trip_type: 'one_way', date_mode: 'fixed', start_date: '2026-12-24', departure_min: '00:00' },
      '2026-11-24',
    );
    const [quotes, rejected] = decode(html, req, 'u', [['VCE'], ['BCN']]);
    expect({ quotes: quotes.map(({ checked_at, ...q }) => q), rejected }).toEqual(want);
    expect(quotes.length).toBeGreaterThan(0);
  });
});
