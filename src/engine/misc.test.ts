import { describe, expect, it } from 'vitest';
import golden from './__fixtures__/misc.json';
import { countries, details, search } from './airports';
import { parseRequest } from './flexible';

describe('request validation, identical to the Python engine', () => {
  for (const [i, c] of golden.requests.entries()) {
    it(`${i}: ${JSON.stringify(c.spec).slice(0, 90)}`, () => {
      let got: unknown;
      try {
        got = { ok: parseRequest(c.spec, golden.today) };
      } catch (exc) {
        got = { error: (exc as Error).message };
      }
      expect(got).toEqual('ok' in c ? { ok: c.ok } : { error: c.error });
    });
  }
});

describe('airports, identical to the Python engine', () => {
  for (const [word, hits] of Object.entries(golden.search)) {
    it(`search "${word}"`, () => expect(search(word)).toEqual(hits));
  }
  for (const [origins, grouped] of Object.entries(golden.countries)) {
    it(`countries ${origins}`, () => expect(countries(origins.split(','))).toEqual(grouped));
  }
  it('details', () => {
    for (const [code, info] of Object.entries(golden.details)) expect(details(code)).toEqual(info);
  });
});
