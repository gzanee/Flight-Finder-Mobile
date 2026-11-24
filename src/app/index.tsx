/** Search form: the same single form as the web page, laid out for a phone. */
import { Link, router, Stack } from 'expo-router';
import { useState } from 'react';
import { Alert, KeyboardAvoidingView, Platform, Pressable, ScrollView, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { DESTINATION_SETS, candidateRoutes } from '../engine/airports';
import { SearchValidationError, buildPlan, parseRequest } from '../engine/flexible';
import { secondsPerSearch } from '../engine/live';
import { addDays, today } from '../engine/time';
import { DAY_LETTERS, countLabel } from '../app-core/format';
import { FormError, anytimePreset, setDateMode, toSpec, weekendPreset } from '../app-core/form';
import { jobs, live, useForm } from '../app-core/store';
import {
  Button,
  Card,
  Chip,
  Collapsible,
  DaysPicker,
  Field,
  Hint,
  Label,
  Notice,
  OptionList,
  PickerField,
  Segmented,
  Stepper,
  ToggleRow,
  styles,
} from '../ui/kit';
import { space, useColors } from '../ui/theme';

const SEATS: [string, string][] = [
  ['economy', 'Economy'],
  ['premium-economy', 'Premium economy'],
  ['business', 'Business'],
  ['first', 'Prima'],
];
const BAGS: [string, string][] = [
  ['base', 'Solo bagaglio piccolo'],
  ['cabin', 'Con trolley in cabina'],
  ['checked', 'Con valigia in stiva'],
];

export default function SearchForm() {
  const c = useColors();
  const s = styles(c);
  const insets = useSafeAreaInsets();
  const [f, setForm] = useForm();
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (patch: Partial<typeof f>) => setForm((prev) => ({ ...prev, ...patch }));
  const round = f.tripType === 'round_trip';
  const flexible = f.dateMode === 'flexible';
  const now = today();

  const start = async () => {
    setError('');
    if (jobs.running) {
      setError('Una ricerca è già in corso: aprila da «Le tue ricerche» e fermala prima di avviarne un’altra.');
      return;
    }
    setBusy(true);
    try {
      const request = parseRequest(toSpec(f));
      const plan = buildPlan(request, candidateRoutes(request));
      const minutes = Math.round((plan.searches_upper_bound * secondsPerSearch(request, live.requestInterval)) / 60);
      const go = async () => {
        const id = await jobs.create(request, plan);
        router.push(`/search/${id}`);
      };
      if (minutes >= 20) {
        Alert.alert(
          'Ricerca lunga',
          `Fino a ${plan.searches_upper_bound.toLocaleString('it-IT')} richieste a Google, circa ${minutes >= 90 ? `${Math.round(minutes / 6) / 10} ore` : `${minutes} minuti`}. ` +
            'Tieni l’app aperta: lo schermo resta acceso. I primi voli diretti arrivano subito.',
          [
            { text: 'Annulla', style: 'cancel' },
            { text: 'Avvia', onPress: () => go().catch((e) => setError(e.message)) },
          ],
        );
      } else {
        await go();
      }
    } catch (exc) {
      setError(exc instanceof SearchValidationError || exc instanceof FormError ? exc.message : (exc as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const placeChips = (kind: 'origins' | 'destinations') => {
    const list = kind === 'origins' ? f.origins : f.destinations;
    return (
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: space(2) }}>
        {list.map((p) => (
          <Chip
            key={p.kind === 'country' ? p.key : p.code}
            label={p.label}
            detail={p.kind === 'country' ? `${p.codes.length} aeroporti` : p.code}
            onRemove={() =>
              set({ [kind]: list.filter((x) => x !== p) } as Partial<typeof f>)
            }
          />
        ))}
        <Chip label="+ Aggiungi" onPress={() => router.push({ pathname: '/place', params: { kind } })} />
      </View>
    );
  };

  return (
    <KeyboardAvoidingView style={s.screen} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <Stack.Screen
        options={{
          headerRight: () => (
            <Link href="/history" asChild>
              <Pressable hitSlop={10}>
                <Text style={{ color: c.primary, fontSize: 16, fontWeight: '600' }}>Ricerche</Text>
              </Pressable>
            </Link>
          ),
        }}
      />
      <ScrollView contentContainerStyle={{ padding: space(4), gap: space(4), paddingBottom: insets.bottom + space(8) }} keyboardShouldPersistTaps="handled">
        <View style={{ backgroundColor: c.hero, borderRadius: 20, padding: space(5), gap: space(2) }}>
          <Text style={{ color: c.heroText, fontSize: 26, fontWeight: '800', lineHeight: 31 }}>Trova il volo giusto,{'\n'}al prezzo giusto.</Text>
          <Text style={{ color: c.heroText, opacity: 0.85, fontSize: 15, lineHeight: 21 }}>
            Tutte le compagnie, da più aeroporti insieme. Hai date flessibili? Troviamo noi i giorni più convenienti.
          </Text>
        </View>

        <Card style={{ gap: space(4) }}>
          <Segmented
            options={[
              ['round_trip', 'Andata e ritorno'],
              ['one_way', 'Solo andata'],
            ]}
            value={f.tripType}
            onChange={(v) => set({ tripType: v })}
          />
          <Segmented
            options={[
              ['fixed', 'Date esatte'],
              ['flexible', 'Date flessibili'],
            ]}
            value={f.dateMode}
            onChange={(v) => setForm((prev) => setDateMode(prev, v))}
          />
          <View style={s.row}>
            <Button small kind="secondary" title="Un weekend" onPress={() => setForm(weekendPreset)} style={{ flex: 1 }} />
            <Button small kind="secondary" title="Qualsiasi giorno" onPress={() => setForm(anytimePreset)} style={{ flex: 1 }} />
          </View>

          <View>
            <Label>Da</Label>
            {placeChips('origins')}
          </View>

          <View style={{ gap: space(2) }}>
            <ToggleRow label="A: ovunque" value={f.anywhere} onChange={(v) => set({ anywhere: v })} />
            {f.anywhere ? (
              <OptionList
                options={[['real', 'Tutte le mete raggiungibili'], ...Object.entries(DESTINATION_SETS).map(([k, v]) => [k, v.label] as [string, string])]}
                value={f.destinationSet}
                onChange={(v) => set({ destinationSet: v })}
              />
            ) : (
              placeChips('destinations')
            )}
          </View>

          <View style={s.row}>
            <PickerField
              label={flexible ? 'Dal' : 'Partenza'}
              mode="date"
              value={f.startDate}
              minimum={now}
              onChange={(v) => setForm((prev) => ({ ...prev, startDate: v, endDate: prev.endDate < v ? addDays(v, flexible ? 30 : 2) : prev.endDate }))}
            />
            {round || flexible ? (
              <PickerField label={flexible ? 'Al' : 'Ritorno'} mode="date" value={f.endDate} minimum={f.startDate} onChange={(v) => set({ endDate: v })} />
            ) : null}
          </View>
          {flexible ? (
            <Hint>
              {round
                ? 'Confrontiamo tutte le partenze e i ritorni possibili in questo periodo, nei giorni e con le notti che scegli.'
                : 'Confrontiamo tutte le partenze possibili in questo periodo, nei giorni che scegli.'}
            </Hint>
          ) : null}

          <View style={{ gap: space(2) }}>
            <Label>Andata</Label>
            {flexible ? <DaysPicker labels={DAY_LETTERS} value={f.departureDays} onChange={(v) => set({ departureDays: v })} /> : null}
            <View style={s.row}>
              <PickerField label="dalle" mode="time" value={f.departureMin} onChange={(v) => set({ departureMin: v })} />
              <PickerField label="alle" mode="time" value={f.departureMax} onChange={(v) => set({ departureMax: v })} />
            </View>
          </View>
          {round ? (
            <View style={{ gap: space(2) }}>
              <Label>Ritorno</Label>
              {flexible ? <DaysPicker labels={DAY_LETTERS} value={f.returnDays} onChange={(v) => set({ returnDays: v })} /> : null}
              <View style={s.row}>
                <PickerField label="dalle" mode="time" value={f.returnMin} onChange={(v) => set({ returnMin: v })} />
                <PickerField label="alle" mode="time" value={f.returnMax} onChange={(v) => set({ returnMax: v })} />
              </View>
            </View>
          ) : null}
          {round && flexible ? (
            <View>
              <Stepper label="Notti, minimo" value={f.minNights} min={0} max={30} onChange={(n) => set({ minNights: n, maxNights: Math.max(n, f.maxNights) })} />
              <Stepper label="Notti, massimo" value={f.maxNights} min={0} max={30} onChange={(n) => set({ maxNights: n, minNights: Math.min(n, f.minNights) })} />
            </View>
          ) : null}
        </Card>

        <Card style={{ gap: space(3) }}>
          <Collapsible
            title="Viaggiatori"
            right={`${[
              countLabel(f.adults, 'adulto', 'adulti'),
              f.children ? countLabel(f.children, 'bambino', 'bambini') : '',
              f.infants ? countLabel(f.infants, 'neonato', 'neonati') : '',
            ]
              .filter(Boolean)
              .join(', ')} · ${SEATS.find(([k]) => k === f.seat)?.[1]}`}
          >
            <View>
              <Stepper label="Adulti" hint="12 anni e più" value={f.adults} min={1} max={9} onChange={(n) => set({ adults: n })} />
              <Stepper label="Bambini" hint="2–11 anni, con posto" value={f.children} min={0} max={8} onChange={(n) => set({ children: n })} />
              <Stepper label="Neonati" hint="sotto i 2 anni, in braccio" value={f.infants} min={0} max={8} onChange={(n) => set({ infants: n })} />
            </View>
            <Label>Classe</Label>
            <OptionList options={SEATS} value={f.seat} onChange={(v) => set({ seat: v as typeof f.seat })} />
            <Label>Bagaglio</Label>
            <OptionList options={BAGS} value={f.baggage} onChange={(v) => set({ baggage: v as typeof f.baggage })} />
          </Collapsible>
          <View style={s.row}>
            <Field
              label={round ? 'Budget A/R' : 'Budget'}
              value={f.budget}
              onChangeText={(v) => set({ budget: v.replace(/[^\d.,]/g, '') })}
              placeholder="Nessun limite"
              keyboardType="decimal-pad"
              suffix="€"
            />
          </View>
          <Label>Scali</Label>
          <Segmented
            options={[
              [0, 'Diretti'],
              [1, '1 scalo'],
              [2, '2 scali'],
              [3, 'Qualsiasi'],
            ]}
            value={f.maxStops}
            onChange={(v) => set({ maxStops: v })}
          />
        </Card>

        <Card>
          <Collapsible title="Altri filtri">
            <Label>Arrivi</Label>
            <View style={s.row}>
              <PickerField label="Arrivo andata dalle" mode="time" value={f.departureArrivalMin} onChange={(v) => set({ departureArrivalMin: v })} />
              <PickerField label="entro le" mode="time" value={f.departureArrivalMax} onChange={(v) => set({ departureArrivalMax: v })} />
            </View>
            {round ? (
              <View style={s.row}>
                <PickerField label="Arrivo ritorno dalle" mode="time" value={f.returnArrivalMin} onChange={(v) => set({ returnArrivalMin: v })} />
                <PickerField label="entro le" mode="time" value={f.returnArrivalMax} onChange={(v) => set({ returnArrivalMax: v })} />
              </View>
            ) : null}
            <PickerField
              label={round ? 'A casa entro le (giorno del ritorno)' : 'Arrivo entro le (giorno della partenza)'}
              mode="time"
              value={f.finalArrival}
              placeholder="Nessun limite"
              onChange={(v) => set({ finalArrival: v })}
              onClear={() => set({ finalArrival: '' })}
            />
            {round ? <Stepper label="Ore minime a destinazione" value={f.minHours} min={0} max={720} onChange={(n) => set({ minHours: n })} /> : null}

            <Label>Durata dei voli (ore)</Label>
            <View style={s.row}>
              <Field label="Andata da" value={f.departureDurationMin} onChangeText={(v) => set({ departureDurationMin: v })} keyboardType="decimal-pad" />
              <Field label="a" value={f.departureDurationMax} onChangeText={(v) => set({ departureDurationMax: v })} keyboardType="decimal-pad" placeholder="Nessun limite" />
            </View>
            {round ? (
              <View style={s.row}>
                <Field label="Ritorno da" value={f.returnDurationMin} onChangeText={(v) => set({ returnDurationMin: v })} keyboardType="decimal-pad" />
                <Field label="a" value={f.returnDurationMax} onChangeText={(v) => set({ returnDurationMax: v })} keyboardType="decimal-pad" placeholder="Nessun limite" />
              </View>
            ) : null}
            <View style={s.row}>
              <Field label="Scalo minimo (min)" value={f.minLayover} onChangeText={(v) => set({ minLayover: v.replace(/\D/g, '') })} keyboardType="number-pad" />
              <Field label="Scalo massimo (min)" value={f.maxLayover} onChangeText={(v) => set({ maxLayover: v.replace(/\D/g, '') })} keyboardType="number-pad" placeholder="Nessun limite" />
            </View>

            <Label>Compagnie e scali</Label>
            <Segmented
              options={[
                ['all', 'Tutte le compagnie'],
                ['low_cost', 'Solo low cost'],
              ]}
              value={f.airlineMode}
              onChange={(v) => set({ airlineMode: v })}
            />
            <Field label="Solo queste compagnie" value={f.airlines} onChangeText={(v) => set({ airlines: v })} placeholder="Es. FR, U2, V7" autoCapitalize="characters" autoCorrect={false} />
            <ToggleRow label="Escludi le tariffe Basic economy" value={f.excludeBasicEconomy} onChange={(v) => set({ excludeBasicEconomy: v })} />
            <ToggleRow label="Accetta scali con cambio volo a carico mio" value={f.selfTransfer} onChange={(v) => set({ selfTransfer: v })} />
            <ToggleRow label="Accetta il cambio di aeroporto durante lo scalo" value={f.airportChange} onChange={(v) => set({ airportChange: v })} />
            <ToggleRow label="Scali solo in area Schengen" value={f.schengenTransfers} onChange={(v) => set({ schengenTransfers: v })} />

            {round ? (
              <>
                <Label>Rientro a</Label>
                <OptionList
                  options={[
                    ['same', 'Stesso aeroporto di partenza'],
                    ['group', 'Uno qualsiasi tra quelli di partenza'],
                  ]}
                  value={f.returnAirport}
                  onChange={(v) => set({ returnAirport: v })}
                />
                {!f.anywhere ? (
                  <>
                    <Label>Ripartenza da</Label>
                    <OptionList
                      options={[
                        ['same', 'Stesso aeroporto di arrivo'],
                        ['group', 'Uno qualsiasi tra le destinazioni scelte'],
                      ]}
                      value={f.returnDeparture}
                      onChange={(v) => set({ returnDeparture: v })}
                    />
                    <Hint>Se riparti da un altro aeroporto, il trasferimento è a carico tuo.</Hint>
                  </>
                ) : null}
              </>
            ) : null}
          </Collapsible>
        </Card>

        {error ? <Notice tone="bad">{error}</Notice> : null}
        <Button title="Cerca voli" onPress={start} loading={busy} />
        <Hint>
          Flight Finder confronta i prezzi pubblicati su Google Flights, dalla connessione di questo telefono. Prezzi e disponibilità possono cambiare:
          verifica sempre le condizioni sul sito del venditore prima di prenotare.
        </Hint>
        <Link href="/diagnostics" style={{ color: c.primary, fontSize: 14 }}>
          Prova la connessione a Google
        </Link>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
