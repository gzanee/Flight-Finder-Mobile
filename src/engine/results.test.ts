import { describe, expect, it } from 'vitest';
import cases from './__fixtures__/browse.json';
import type { Trip } from './flexible';
import { ResultFilters, browseResults } from './results';

describe('result browser, identical to the Python engine', () => {
  for (const [i, c] of cases.entries()) {
    it(`case ${i}: ${c.sort} ${JSON.stringify(c.args)}`, () => {
      const result = browseResults(c.trips as unknown as Trip[], c.sort, c.offset, ResultFilters.parse(c.args), c.focus);
      expect(JSON.parse(JSON.stringify(result))).toEqual(c.result);
    });
  }
});
