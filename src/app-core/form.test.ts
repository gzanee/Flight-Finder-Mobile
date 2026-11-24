import { describe, expect, it } from 'vitest';
import golden from '../engine/__fixtures__/jobs.json';
import { parseRequest } from '../engine/flexible';
import { FormError, anytimePreset, fromRequest, initialForm, setDateMode, toSpec, weekendPreset } from './form';

const TODAY = golden.today;

describe('search form', () => {
  it('the initial form is a valid weekend-style search from Venice and Treviso', () => {
    const r = parseRequest(toSpec(initialForm(TODAY)), TODAY);
    expect(r.origins).toEqual(['VCE', 'TSF']);
    expect(r.destinations).toEqual([]);
    expect(r.budget).toBe(100000);
    expect(r.max_stops).toBe(1);
  });

  it('presets match the web page', () => {
    const w = weekendPreset(anytimePreset(initialForm(TODAY)));
    expect([w.departureDays, w.returnDays, w.departureMin, w.returnMin, w.minNights]).toEqual([[4], [6], '17:00', '17:00', 2]);
    expect(anytimePreset(w).departureDays).toHaveLength(7);
  });

  it('exact dates shorten a long period', () => {
    const f = { ...initialForm(TODAY), startDate: '2027-01-10', endDate: '2027-03-01' };
    expect(setDateMode(f, 'fixed').endDate).toBe('2027-01-13');
  });

  it('a country destination expands to its airports, without duplicates', () => {
    const f = {
      ...initialForm(TODAY),
      anywhere: false,
      destinations: [
        { kind: 'airport' as const, code: 'BCN', label: 'Barcellona' },
        { kind: 'country' as const, key: '@es', country: 'es', label: 'Spagna', codes: ['MAD', 'BCN'], cities: [] },
      ],
    };
    expect(toSpec(f).destinations).toEqual(['BCN', 'MAD']);
  });

  it('asks for a destination or Ovunque', () => {
    expect(() => toSpec({ ...initialForm(TODAY), anywhere: false })).toThrow(FormError);
  });

  for (const [name, s] of Object.entries(golden.scenarios)) {
    it(`a saved search (${name}) comes back unchanged through the form`, () => {
      const r = parseRequest(s.spec, TODAY);
      const back = parseRequest(toSpec(fromRequest(r, (c) => c)), TODAY);
      // One-way: the return window is unused; the form resets it like the web page.
      const used = (x: typeof r) =>
        x.trip_type === 'one_way' ? Object.fromEntries(Object.entries(x).filter(([k]) => !k.startsWith('return_'))) : x;
      expect(used(back)).toEqual(used(r));
    });
  }
});
