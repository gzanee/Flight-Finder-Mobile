/** A search: live progress while it runs, then the saved results to explore. */
import { router, Stack, useLocalSearchParams } from 'expo-router';
import { useMemo, useState } from 'react';
import { FlatList, Modal, Pressable, ScrollView, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import type { Trip } from '../../engine/flexible';
import { ResultFilters, type Sort } from '../../engine/results';
import { city, countLabel, duration, flag, money, monthTitle, remaining, shortDay } from '../../app-core/format';
import { fromRequest } from '../../app-core/form';
import { formStore, jobs, type JobView, useJobView } from '../../app-core/store';
import { Button, Card, Chip, Collapsible, Field, Hint, Label, Notice, OptionList, PickerField, Segmented, styles, tap } from '../../ui/kit';
import { radius, space, useColors } from '../../ui/theme';
import { TripCard } from '../../ui/trip';

const STATES: Record<string, string> = {
  running: 'Stiamo cercando i voli migliori…',
  cancelling: 'Ci fermiamo…',
  cancelled: 'Ricerca fermata',
  blocked: 'Ricerca in pausa',
  interrupted: 'Ricerca interrotta',
  complete: 'Ricerca completata',
};
const SORTS: [Sort, string][] = [
  ['price', 'Prezzo più basso'],
  ['useful', 'Più tempo a destinazione'],
  ['duration', 'Voli più brevi'],
  ['departure', 'Partenza più vicina'],
];

type Filters = Record<string, string>;

/** One option per city; the value lists its airports (e.g. "LGW,LTN,STN"). */
function cityOptions(codes: string[]): [string, string][] {
  const cities = new Map<string, string[]>();
  for (const code of codes) cities.set(city(code), [...(cities.get(city(code)) ?? []), code]);
  return [...cities]
    .map(([name, list]): [string, string] => [list.sort().join(','), `${name} (${list.join(', ')})`])
    .sort((a, b) => a[1].localeCompare(b[1], 'it'));
}

export default function SearchScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const c = useColors();
  const s = styles(c);
  const insets = useSafeAreaInsets();
  const [sort, setSort] = useState<Sort>('price');
  const [offset, setOffset] = useState(0);
  const [filters, setFilters] = useState<Filters>({});
  const [focus, setFocus] = useState('');
  const [mode, setMode] = useState<'destinations' | 'trips' | null>(null);
  const [showFilters, setShowFilters] = useState(false);
  const { view, error, refresh } = useJobView(id, { sort, offset, filters, focus });

  const viewMode = mode ?? (view && view.plan.candidate_count > 1 ? 'destinations' : 'trips');
  const running = view ? view.status === 'running' || view.status === 'cancelling' : false;
  const round = view ? view.request.trip_type !== 'one_way' : true;
  const activeFilters = Object.values(filters).filter(Boolean).length;

  const changeFilters = (next: Filters) => {
    setFilters(next);
    setOffset(0);
    setFocus('');
  };

  const openTrip = (trip: Trip) => router.push({ pathname: '/trip', params: { job: id, trip: trip.id } });

  const header = view ? (
    <View style={{ gap: space(4), paddingBottom: space(3) }}>
      <Stack.Screen
        options={{
          title: summaryTitle(view),
          headerRight: running
            ? undefined
            : () => (
                <Pressable
                  hitSlop={10}
                  onPress={() => {
                    formStore.set(fromRequest(view.request, (code) => city(code)));
                    router.dismissTo('/');
                  }}
                >
                  <Text style={{ color: c.primary, fontSize: 16, fontWeight: '600' }}>Modifica</Text>
                </Pressable>
              ),
        }}
      />
      {view.status !== 'complete' || view.issues.length ? <Progress view={view} onRefresh={refresh} /> : null}
      <View>
        <Text style={s.h2}>
          {view.filtered_count
            ? `${countLabel(view.filtered_count, 'viaggio', 'viaggi')} ${round ? 'andata e ritorno' : 'di sola andata'}`
            : running
              ? 'Cerchiamo…'
              : 'Nessun viaggio'}
        </Text>
        <Hint>
          {view.status === 'complete' ? '' : 'I risultati si aggiornano mentre cerchiamo. '}Prezzi totali per{' '}
          {countLabel(view.request.adults + view.request.children + view.request.infants, 'viaggiatore', 'viaggiatori')}, orari locali.
        </Hint>
      </View>
      <Recommendations view={view} round={round} onOpen={(tripId) => {
        setFocus(tripId);
        setMode('trips');
      }} />
      {view.calendar.length > 1 ? (
        <Card>
          <Collapsible title="Prezzi per data">
            <Calendar items={view.calendar} />
          </Collapsible>
        </Card>
      ) : null}
      <View style={{ flexDirection: 'row', gap: space(2), alignItems: 'center' }}>
        <View style={{ flex: 1 }}>
          <Segmented
            options={[
              ['destinations', 'Per destinazione'],
              ['trips', 'Tutti i voli'],
            ]}
            value={viewMode}
            onChange={(v) => setMode(v)}
          />
        </View>
        <Button small kind="secondary" title={activeFilters ? `Filtri (${activeFilters})` : 'Filtri'} onPress={() => setShowFilters(true)} />
      </View>
      {filters.destination ? (
        <Pressable onPress={() => { changeFilters({ ...filters, destination: '' }); setMode('destinations'); }}>
          <Text style={{ color: c.primary, fontWeight: '600' }}>← Tutte le destinazioni</Text>
        </Pressable>
      ) : null}
      {viewMode === 'trips' ? (
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: space(2) }}>
          {SORTS.filter(([k]) => round || k !== 'useful').map(([k, label]) => (
            <Chip key={k} label={label} selected={sort === k} onPress={() => { setSort(k); setOffset(0); }} />
          ))}
        </ScrollView>
      ) : null}
      {view.focused_id === null && focus ? <Hint tone="warn">Questo viaggio non è più tra i risultati.</Hint> : null}
    </View>
  ) : undefined;

  const empty = view ? (
    <Card style={{ alignItems: 'center', gap: space(2) }}>
      <Text style={s.h2}>{view.result_count ? 'Nessun viaggio con questi filtri' : running ? 'Stiamo cercando…' : 'Nessun volo trovato'}</Text>
      <Hint>
        {view.result_count
          ? 'Prova ad alzare il prezzo massimo o ad azzerare i filtri.'
          : running
            ? 'I primi voli compaiono qui appena li troviamo.'
            : 'Prova altre date, aggiungi aeroporti vicini, accetta uno scalo o alza il budget.'}
      </Hint>
      {view.result_count ? <Button small kind="secondary" title="Azzera filtri" onPress={() => changeFilters({})} /> : null}
    </Card>
  ) : undefined;

  const cityValues = useMemo(
    () => new Map(cityOptions(view?.facets.destinations ?? []).map(([value]) => [city(value.split(',')[0]), value])),
    [view?.facets.destinations.join(',')],
  );

  if (!view) {
    return (
      <View style={[s.screen, { padding: space(4) }]}>
        {error ? <Notice tone="bad">{error}</Notice> : <Hint>Carico la ricerca…</Hint>}
      </View>
    );
  }

  const footer =
    viewMode === 'trips' && (view.has_more || offset > 0) ? (
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: space(2), paddingTop: space(3) }}>
        <Button small kind="secondary" title="← Precedenti" disabled={offset === 0} onPress={() => setOffset(Math.max(0, offset - 50))} />
        <Text style={{ flex: 1, textAlign: 'center', color: c.muted }}>
          {view.offset + 1}–{view.offset + view.results.length} di {view.filtered_count}
        </Text>
        <Button small kind="secondary" title="Successivi →" disabled={!view.has_more} onPress={() => setOffset(offset + 50)} />
      </View>
    ) : undefined;

  return (
    <View style={s.screen}>
      {viewMode === 'destinations' ? (
        <FlatList
          contentContainerStyle={{ padding: space(4), gap: space(3), paddingBottom: insets.bottom + space(8) }}
          data={view.groups}
          keyExtractor={(g) => g.city}
          ListHeaderComponent={header}
          ListEmptyComponent={empty}
          renderItem={({ item: g }) => (
            <Pressable
              accessibilityRole="button"
              onPress={() => {
                tap();
                changeFilters({ ...filters, destination: cityValues.get(g.city) || g.destination });
                setMode('trips');
              }}
              style={({ pressed }) => ({
                backgroundColor: c.card,
                borderRadius: radius.l,
                borderWidth: 1,
                borderColor: c.border,
                padding: space(4),
                flexDirection: 'row',
                alignItems: 'center',
                gap: space(3),
                opacity: pressed ? 0.8 : 1,
              })}
            >
              <View style={{ flex: 1, gap: 2 }}>
                <Text style={{ color: c.text, fontSize: 17, fontWeight: '800' }}>
                  {flag(g.destinations[0])} {g.city}
                </Text>
                <Text style={{ color: c.muted, fontSize: 12 }} numberOfLines={1}>
                  {g.destinations.length === 1 ? view.airports[g.destinations[0]]?.name : `${g.destinations.length} aeroporti · ${g.destinations.join(', ')}`}
                </Text>
                <Text style={{ color: c.muted, fontSize: 13 }}>
                  {shortDay(g.featured.date)}
                  {g.featured.return_date ? ` – ${shortDay(g.featured.return_date)}` : ''} · da {city(g.featured.origin)}
                </Text>
              </View>
              <View style={{ alignItems: 'flex-end' }}>
                <Text style={{ color: c.muted, fontSize: 11 }}>da</Text>
                <Text style={{ color: c.text, fontSize: 19, fontWeight: '800' }}>{money(g.cheapest.price)}</Text>
                <Text style={{ color: c.primary, fontSize: 12, fontWeight: '600' }}>{countLabel(g.count, 'opzione', 'opzioni')} →</Text>
              </View>
            </Pressable>
          )}
        />
      ) : (
        <FlatList
          contentContainerStyle={{ padding: space(4), gap: space(3), paddingBottom: insets.bottom + space(8) }}
          data={view.results}
          keyExtractor={(t) => t.id}
          ListHeaderComponent={header}
          ListEmptyComponent={empty}
          ListFooterComponent={footer}
          renderItem={({ item }) => <TripCard trip={item} highlighted={item.id === view.focused_id} onPress={() => openTrip(item)} />}
        />
      )}
      <FiltersSheet
        visible={showFilters}
        onClose={() => setShowFilters(false)}
        view={view}
        filters={filters}
        round={round}
        onChange={changeFilters}
      />
    </View>
  );
}

function summaryTitle(view: JobView) {
  const r = view.request;
  const to = r.destinations.length ? (r.destinations.length > 2 ? `${r.destinations.length} mete` : r.destinations.map(city).join(', ')) : 'Ovunque';
  const title = `${r.origins.map(city).join(' + ')} → ${to}`;
  return title.length > 26 ? `${title.slice(0, 25)}…` : title;
}

function Progress({ view, onRefresh }: { view: JobView; onRefresh: () => void }) {
  const c = useColors();
  const running = view.status === 'running' || view.status === 'cancelling';
  const share = view.total ? Math.min(1, view.completed / view.total) : 0;
  const r = view.request;
  const current = view.current
    ?.replace(/\b[A-Z0-9]{3}\b/g, (code) => view.airports[code]?.city || city(code))
    .replace(/\d{4}-\d{2}-\d{2}/g, (day) => shortDay(day));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  return (
    <Card style={{ gap: space(2.5) }}>
      <Text style={{ color: c.text, fontSize: 17, fontWeight: '800' }}>{STATES[view.status] ?? view.status}</Text>
      <Hint>
        {r.origins.map(city).join(' + ')} → {r.destinations.length ? r.destinations.map(city).join(', ') : 'ovunque'} · {shortDay(r.start_date)}
        {r.start_date === r.end_date ? '' : ` – ${shortDay(r.end_date)}`} · {countLabel(r.adults + r.children + r.infants, 'viaggiatore', 'viaggiatori')}
      </Hint>
      <View style={{ height: 8, backgroundColor: c.chip, borderRadius: 4, overflow: 'hidden' }}>
        <View style={{ width: `${share * 100}%`, height: 8, backgroundColor: view.status === 'blocked' ? c.warn : c.primary }} />
      </View>
      <Text style={{ color: c.text, fontSize: 14 }}>
        {countLabel(view.result_count, 'viaggio trovato', 'viaggi trovati')}
        {running ? ` finora · ancora ${remaining(view.eta_seconds)}` : ''}
      </Text>
      {running && current ? <Hint>Ora: {current}</Hint> : null}
      {running ? <Hint tone="warn">Tieni l’app aperta: lo schermo resta acceso. Se esci, la ricerca si ferma e potrai riprenderla.</Hint> : null}
      {view.issues.length ? <Notice>{view.issues.join(' ')}</Notice> : null}
      {error ? <Notice tone="bad">{error}</Notice> : null}
      {running ? (
        <Button kind="danger" small title="Ferma" disabled={view.status === 'cancelling'} onPress={() => jobs.cancel(view.id).then(onRefresh)} />
      ) : ['cancelled', 'blocked', 'interrupted'].includes(view.status) ? (
        <Button
          small
          title="Riprendi"
          loading={busy}
          onPress={async () => {
            setBusy(true);
            setError('');
            try {
              await jobs.resume(view.id);
            } catch (exc) {
              setError((exc as Error).message);
            } finally {
              setBusy(false);
              onRefresh();
            }
          }}
        />
      ) : null}
    </Card>
  );
}

function Recommendations({ view, round, onOpen }: { view: JobView; round: boolean; onOpen: (id: string) => void }) {
  const c = useColors();
  const r = view.recommendations;
  if (!r.cheapest) return null;
  const cards: [string, typeof r.cheapest | undefined, string][] = [['Il più economico', r.cheapest, 'Il prezzo più basso trovato.']];
  if (round) {
    cards.push([
      'Più tempo a destinazione',
      r.longest,
      r.longest?.id === r.cheapest?.id ? 'Ed è anche il più economico.' : r.longest ? 'Il soggiorno più lungo.' : 'Nessun confronto possibile.',
    ]);
    cards.push([
      'Il miglior compromesso',
      r.balanced,
      r.balanced ? `${money(r.balanced.extra_price)} in più, ${duration(r.balanced.extra_minutes)} in più sul posto.` : 'Il più economico è già la scelta migliore.',
    ]);
  }
  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: space(3) }}>
      {cards.map(([title, trip, why]) => (
        <Pressable
          key={title}
          disabled={!trip}
          onPress={() => trip && onOpen(trip.id)}
          style={({ pressed }) => ({
            width: 220,
            backgroundColor: c.card,
            borderRadius: radius.l,
            borderWidth: 1,
            borderColor: c.border,
            padding: space(3.5),
            gap: 3,
            opacity: pressed ? 0.8 : 1,
          })}
        >
          <Text style={{ color: c.primary, fontSize: 12, fontWeight: '800', textTransform: 'uppercase' }}>{title}</Text>
          {trip ? (
            <>
              <Text style={{ color: c.text, fontSize: 16, fontWeight: '800' }} numberOfLines={1}>
                {flag(trip.destination)} {city(trip.destination)}
              </Text>
              <Text style={{ color: c.text, fontSize: 22, fontWeight: '800' }}>{money(trip.price)}</Text>
              <Text style={{ color: c.muted, fontSize: 12 }} numberOfLines={1}>
                {shortDay(trip.date)}
                {trip.return_date ? ` – ${shortDay(trip.return_date)}` : ''} · da {city(trip.origin)}
              </Text>
              {trip.useful_minutes != null ? (
                <Text style={{ color: c.muted, fontSize: 12 }}>
                  {duration(trip.useful_minutes)} {trip.destination_airport_change ? 'tra i voli' : 'a destinazione'}
                </Text>
              ) : null}
            </>
          ) : null}
          <Text style={{ color: c.muted, fontSize: 12, marginTop: 2 }}>{why}</Text>
        </Pressable>
      ))}
    </ScrollView>
  );
}

function Calendar({ items }: { items: JobView['calendar'] }) {
  const c = useColors();
  const byDay = new Map<string, typeof items>();
  for (const item of items) byDay.set(item.date, [...(byDay.get(item.date) ?? []), item]);
  const best = [...byDay.values()].map((list) => Math.min(...list.map((x) => x.price)));
  const lo = Math.min(...best);
  const hi = Math.max(...best);
  const tier = (p: number) => (hi === lo || p <= lo + (hi - lo) / 3 ? c.goodSoft : p <= lo + (2 * (hi - lo)) / 3 ? c.warnSoft : c.badSoft);
  const keys = [...byDay.keys()].sort();
  const months: [number, number][] = [];
  for (let y = +keys[0].slice(0, 4), m = +keys[0].slice(5, 7); `${y}-${String(m).padStart(2, '0')}` <= keys[keys.length - 1].slice(0, 7); m === 12 ? (y++, (m = 1)) : m++) {
    months.push([y, m]);
  }
  return (
    <View style={{ gap: space(4) }}>
      <Hint>Prezzo minimo per giorno di partenza. Verde: più economico.</Hint>
      {months.map(([y, m]) => {
        const first = (new Date(Date.UTC(y, m - 1, 1)).getUTCDay() + 6) % 7;
        const days = new Date(Date.UTC(y, m, 0)).getUTCDate();
        const cells: (number | null)[] = [...Array(first).fill(null), ...Array.from({ length: days }, (_, i) => i + 1)];
        return (
          <View key={`${y}-${m}`} style={{ gap: space(2) }}>
            <Text style={{ color: c.text, fontWeight: '700', textTransform: 'capitalize' }}>{monthTitle(y, m)}</Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap' }}>
              {['lun', 'mar', 'mer', 'gio', 'ven', 'sab', 'dom'].map((w) => (
                <Text key={w} style={{ width: '14.28%', textAlign: 'center', color: c.faint, fontSize: 11 }}>
                  {w}
                </Text>
              ))}
              {cells.map((d, i) => {
                const key = d ? `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}` : '';
                const list = d ? byDay.get(key) : undefined;
                const price = list ? Math.min(...list.map((x) => x.price)) : null;
                return (
                  <View key={i} style={{ width: '14.28%', padding: 1.5 }}>
                    {d ? (
                      <View
                        style={{
                          borderRadius: 6,
                          paddingVertical: 4,
                          alignItems: 'center',
                          backgroundColor: price !== null ? tier(price) : 'transparent',
                          borderWidth: price === lo ? 1.5 : 0,
                          borderColor: c.good,
                        }}
                      >
                        <Text style={{ color: price !== null ? c.text : c.faint, fontSize: 12, fontWeight: '700' }}>{d}</Text>
                        {price !== null ? (
                          <Text style={{ color: c.text, fontSize: 9.5 }} numberOfLines={1}>
                            {money(Math.round(price))}
                          </Text>
                        ) : null}
                      </View>
                    ) : null}
                  </View>
                );
              })}
            </View>
          </View>
        );
      })}
    </View>
  );
}

function FiltersSheet({
  visible,
  onClose,
  view,
  filters,
  round,
  onChange,
}: {
  visible: boolean;
  onClose: () => void;
  view: JobView;
  filters: Filters;
  round: boolean;
  onChange: (f: Filters) => void;
}) {
  const c = useColors();
  const s = styles(c);
  const insets = useSafeAreaInsets();
  const [draft, setDraft] = useState<Filters>(filters);
  const [error, setError] = useState('');
  const set = (key: string, value: string) => setDraft((d) => ({ ...d, [key]: value }));
  const excluded = (draft.exclude ?? '').split(',').filter(Boolean);
  const cities = cityOptions(view.facets.destinations);
  const apply = () => {
    try {
      ResultFilters.parse(draft); // validated by the engine, like the web page
      setError('');
      onChange(draft);
      onClose();
    } catch (exc) {
      setError((exc as Error).message);
    }
  };
  return (
    <Modal visible={visible} animationType="slide" presentationStyle="pageSheet" onRequestClose={onClose} onShow={() => setDraft(filters)}>
      <View style={[s.screen, { paddingTop: space(4) }]}>
        <View style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: space(4), paddingBottom: space(2) }}>
          <Text style={[s.title, { flex: 1 }]}>Filtra i risultati</Text>
          <Pressable onPress={onClose} hitSlop={10}>
            <Text style={{ color: c.primary, fontSize: 16 }}>Chiudi</Text>
          </Pressable>
        </View>
        <ScrollView contentContainerStyle={{ padding: space(4), gap: space(4), paddingBottom: insets.bottom + space(8) }}>
          <Hint>Filtri e suggerimenti lavorano su tutti i {view.result_count} viaggi salvati, senza nuove richieste a Google.</Hint>
          <Field label="Prezzo massimo" value={draft.max_price ?? ''} onChangeText={(v) => set('max_price', v.replace(/[^\d.,]/g, '').replace(',', '.'))} keyboardType="decimal-pad" placeholder="Qualsiasi" suffix="€" />
          {round ? (
            <>
              <Label>Tempo a destinazione</Label>
              <Segmented
                options={[
                  ['', 'Qualsiasi'],
                  ['24', '24 h'],
                  ['36', '36 h'],
                  ['48', '48 h'],
                  ['60', '60 h'],
                ]}
                value={draft.min_hours ?? ''}
                onChange={(v) => set('min_hours', v)}
              />
              <PickerField label="Rientro entro le" mode="time" value={draft.home_before ?? ''} placeholder="Qualsiasi ora" onChange={(v) => set('home_before', v)} onClear={() => set('home_before', '')} />
            </>
          ) : null}
          {view.facets.origins.length > 1 ? (
            <>
              <Label>Partenza da</Label>
              <OptionList options={[['', 'Tutti gli aeroporti'], ...view.facets.origins.map((o) => [o, `${city(o)} (${o})`] as [string, string])]} value={draft.origin ?? ''} onChange={(v) => set('origin', v)} />
            </>
          ) : null}
          {cities.length > 1 ? (
            <>
              <Label>Destinazione</Label>
              <OptionList options={[['', 'Tutte'], ...cities]} value={draft.destination ?? ''} onChange={(v) => set('destination', v)} />
            </>
          ) : null}
          {view.facets.airlines.length > 1 ? (
            <>
              <Label>Compagnia (su ogni volo)</Label>
              <OptionList options={[['', 'Tutte'], ...view.facets.airlines.map((a) => [a.code, a.name] as [string, string])]} value={draft.airline ?? ''} onChange={(v) => set('airline', v)} />
            </>
          ) : null}
          {cities.length > 1 ? (
            <>
              <Label>Escludi destinazioni</Label>
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: space(2) }}>
                {cities.map(([value, label]) => {
                  const on = value.split(',').every((code) => excluded.includes(code));
                  return (
                    <Chip
                      key={value}
                      label={label}
                      selected={on}
                      onPress={() => {
                        const codes = value.split(',');
                        set('exclude', (on ? excluded.filter((x) => !codes.includes(x)) : [...excluded, ...codes]).join(','));
                      }}
                    />
                  );
                })}
              </View>
            </>
          ) : null}
          {error ? <Notice tone="bad">{error}</Notice> : null}
          <Button title="Mostra risultati" onPress={apply} />
          <Button
            kind="ghost"
            title="Azzera filtri"
            onPress={() => {
              setDraft({});
              onChange({});
              onClose();
            }}
          />
        </ScrollView>
      </View>
    </Modal>
  );
}
