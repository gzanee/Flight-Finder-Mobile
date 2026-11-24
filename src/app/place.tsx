/** Add an origin or destination: city, airport, IATA code or (for destinations) a whole country. */
import { router, Stack, useLocalSearchParams } from 'expo-router';
import { useMemo, useState } from 'react';
import { FlatList, Pressable, Text, View } from 'react-native';
import countryNames from '../engine/data/countries_it.json';
import { countries, normalize, search } from '../engine/airports';
import { MAX_DESTINATIONS, MAX_ORIGINS } from '../engine/flexible';
import { countLabel, countryFlag } from '../app-core/format';
import { destinationCodes, type Place } from '../app-core/form';
import { useForm } from '../app-core/store';
import { Field, Hint, Notice, tap } from '../ui/kit';
import { space, useColors } from '../ui/theme';

const NAMES = countryNames as Record<string, string>;

type Hit =
  | { type: 'airport'; code: string; name: string; city: string }
  | { type: 'country'; country: string; name: string; linked: boolean; airports: { code: string; city: string }[] };

export default function PlaceScreen() {
  const { kind } = useLocalSearchParams<{ kind: 'origins' | 'destinations' }>();
  const c = useColors();
  const [form, setForm] = useForm();
  const [query, setQuery] = useState('');
  const [error, setError] = useState('');
  const isOrigin = kind === 'origins';
  const chosen = isOrigin ? form.origins : form.destinations;
  const originCodes = form.origins.map((p) => (p.kind === 'airport' ? p.code : '')).filter(Boolean);
  const catalog = useMemo(() => (isOrigin ? {} : countries(originCodes)), [isOrigin, originCodes.join(',')]);

  const hits: Hit[] = useMemo(() => {
    const q = query.trim();
    if (q.length < 2) return [];
    const taken = new Set(isOrigin ? originCodes : destinationCodes(form));
    const airports: Hit[] = search(q, 12)
      .filter((a) => !taken.has(a.code))
      .map((a) => ({ type: 'airport', code: a.code, name: a.name, city: a.city }));
    if (isOrigin) return airports;
    const needle = normalize(q);
    const nations: Hit[] = Object.entries(catalog)
      .map(([country, entry]) => ({ type: 'country' as const, country, name: NAMES[country] ?? country.toUpperCase(), ...entry }))
      .filter((h) => normalize(h.name).startsWith(needle) || h.country === needle)
      .filter((h) => !chosen.some((p) => p.kind === 'country' && p.country === h.country))
      .sort((a, b) => a.name.localeCompare(b.name, 'it'))
      .slice(0, 3);
    return [...nations, ...airports];
  }, [query, catalog, form]);

  const add = (hit: Hit) => {
    tap();
    let place: Place;
    if (hit.type === 'country') {
      const codes = hit.airports.map((a) => a.code);
      if (new Set([...destinationCodes(form), ...codes]).size > MAX_DESTINATIONS) {
        setError('Hai raggiunto il numero massimo di aeroporti.');
        return;
      }
      place = { kind: 'country', key: `@${hit.country}`, country: hit.country, label: hit.name, codes, cities: hit.airports.map((a) => a.city) };
      setForm((f) => ({
        ...f,
        anywhere: false,
        // The country already includes its airports.
        destinations: [...f.destinations.filter((p) => !(p.kind === 'airport' && codes.includes(p.code))), place],
      }));
    } else {
      const limit = isOrigin ? MAX_ORIGINS : MAX_DESTINATIONS;
      if (chosen.length >= limit) {
        setError('Hai raggiunto il numero massimo di aeroporti.');
        return;
      }
      place = { kind: 'airport', code: hit.code, label: hit.city || hit.name };
      setForm((f) => (isOrigin ? { ...f, origins: [...f.origins, place] } : { ...f, anywhere: false, destinations: [...f.destinations, place] }));
    }
    setQuery('');
    setError('');
  };

  return (
    <View style={{ flex: 1, backgroundColor: c.bg, padding: space(4), gap: space(3) }}>
      <Stack.Screen
        options={{
          title: isOrigin ? 'Da dove parti' : 'Dove vuoi andare',
          headerRight: () => (
            <Pressable onPress={() => router.back()} hitSlop={10}>
              <Text style={{ color: c.primary, fontSize: 16, fontWeight: '700' }}>Fine</Text>
            </Pressable>
          ),
        }}
      />
      <View>
      <Field
        autoFocus
        value={query}
        onChangeText={setQuery}
        placeholder={isOrigin ? 'Città, aeroporto o codice' : 'Città, aeroporto, codice o paese'}
        autoCorrect={false}
        autoCapitalize="none"
        returnKeyType="search"
        accessibilityLabel="Cerca aeroporto"
      />
      </View>
      {error ? <Notice tone="bad">{error}</Notice> : null}
      {chosen.length ? (
        <Hint>
          Scelti: {chosen.map((p) => (p.kind === 'country' ? `${p.label} (${p.codes.length})` : `${p.label} ${p.code}`)).join(', ')}
        </Hint>
      ) : null}
      <FlatList
        keyboardShouldPersistTaps="handled"
        data={hits}
        keyExtractor={(h) => (h.type === 'country' ? `@${h.country}` : h.code)}
        ItemSeparatorComponent={() => <View style={{ height: 1, backgroundColor: c.border }} />}
        ListEmptyComponent={
          query.trim().length >= 2 ? <Hint>Nessun aeroporto trovato. Prova con il nome inglese o il codice IATA.</Hint> : undefined
        }
        renderItem={({ item }) => (
          <Pressable
            accessibilityRole="button"
            onPress={() => add(item)}
            style={({ pressed }) => ({ paddingVertical: space(3), flexDirection: 'row', alignItems: 'center', gap: space(3), opacity: pressed ? 0.6 : 1 })}
          >
            <View style={{ flex: 1 }}>
              <Text style={{ color: c.text, fontSize: 16, fontWeight: '600' }}>{item.type === 'country' ? item.name : item.city || item.name}</Text>
              <Text style={{ color: c.muted, fontSize: 13 }} numberOfLines={1}>
                {item.type === 'country'
                  ? item.linked
                    ? `Tutto il paese · ${countLabel(item.airports.length, 'aeroporto collegato', 'aeroporti collegati')}`
                    : `Senza voli diretti noti · ${[...new Set(item.airports.map((a) => a.city))].join(', ')}`
                  : item.name}
              </Text>
            </View>
            <Text style={{ color: c.primary, fontWeight: '800', fontSize: item.type === 'country' ? 22 : 15 }}>
              {item.type === 'country' ? countryFlag(item.country) : item.code}
            </Text>
          </Pressable>
        )}
      />
    </View>
  );
}
