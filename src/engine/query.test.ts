import { describe, expect, it } from 'vitest';
import cases from './__fixtures__/queries.json';
import { type QueryRequest, oneWayQuery, roundTripQuery, tfs } from './google';

describe('tfs query, identical to the Python engine', () => {
  for (const [i, c] of cases.entries()) {
    it(`case ${i}: ${c.origins.join('+')}-${c.destinations.join('+')} ${c.trip}`, () => {
      const request = c.request as unknown as QueryRequest;
      const q =
        c.trip === 'round-trip'
          ? roundTripQuery(c.origins[0], c.destinations[0], c.day, c.back as string, request)
          : oneWayQuery(c.origins, c.destinations, c.day, request);
      expect(tfs(q)).toBe(c.tfs);
    });
  }
});
