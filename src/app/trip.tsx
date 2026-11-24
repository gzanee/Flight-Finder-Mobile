/** One trip: flights, price freshness, recheck and the links to book on Google Flights. */
import { router, useLocalSearchParams } from 'expo-router';
import * as WebBrowser from 'expo-web-browser';
import { useEffect, useState } from 'react';
import { ScrollView, Share, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import type { Trip } from '../engine/flexible';
import { JobManager } from '../engine/jobs';
import { airportName, city, countLabel, duration, flag, money, safeLink, shortDay } from '../app-core/format';
import { jobs } from '../app-core/store';
import { Button, Card, Hint, Notice, styles } from '../ui/kit';
import { space, useColors } from '../ui/theme';
import { Leg, tripSummary } from '../ui/trip';
import { roundTripUrl } from '../engine/live';

async function loadTrip(jobId: string, tripId: string): Promise<Trip | null> {
  const job = await jobs.get(jobId);
  const trip = JobManager.currentResults(job).find((t) => t.id === tripId) ?? null;
  if (trip) trip.round_trip_url = roundTripUrl(trip, job.request);
  return trip;
}

export default function TripScreen() {
  const { job, trip: tripId } = useLocalSearchParams<{ job: string; trip: string }>();
  const c = useColors();
  const s = styles(c);
  const insets = useSafeAreaInsets();
  const [trip, setTrip] = useState<Trip | null | undefined>(undefined);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ tone: 'good' | 'warn' | 'bad'; text: string } | null>(null);

  useEffect(() => {
    loadTrip(job, tripId).then(setTrip, () => setTrip(null));
  }, [job, tripId]);

  if (trip === undefined) return <View style={s.screen} />;
  if (trip === null) {
    return (
      <View style={[s.screen, { padding: space(4), gap: space(3) }]}>
        <Notice>{feedback?.text ?? 'Questo viaggio non è più tra i risultati.'}</Notice>
        <Button kind="secondary" title="Torna ai risultati" onPress={() => router.back()} />
      </View>
    );
  }

  const { origin, dest, round, away, home } = tripSummary(trip);
  const checked = new Date(trip.checked_at).toLocaleTimeString('it-IT', { hour: '2-digit', minute: '2-digit' });
  const primary = safeLink(round ? trip.round_trip_url || trip.outbound.booking_url : trip.outbound.booking_url);
  const open = (url: string | null) => {
    if (url) WebBrowser.openBrowserAsync(url).catch(() => undefined);
  };

  const recheck = async () => {
    setBusy(true);
    setFeedback(null);
    try {
      const result = await jobs.recheck(job, trip.id);
      if (result.status === 'confirmed') {
        setFeedback({
          tone: result.price_changed ? 'warn' : 'good',
          text: result.price_changed ? `Il prezzo è cambiato: ora ${money(result.result.price)}.` : 'Prezzo confermato.',
        });
        setTrip(await loadTrip(job, trip.id));
      } else {
        setFeedback({ tone: 'bad', text: result.message });
        setTrip(null);
      }
    } catch (exc) {
      setFeedback({ tone: 'bad', text: `Non è stato possibile aggiornare il prezzo: ${(exc as Error).message}` });
    } finally {
      setBusy(false);
    }
  };

  const share = () => {
    const lines = [
      `${city(origin)} → ${city(dest)} · ${money(trip.price)}${round ? ' andata e ritorno' : ''}`,
      `Andata: ${shortDay(trip.outbound.segments[0].departure)} ${trip.outbound.segments[0].departure.slice(11, 16)}`,
      trip.inbound ? `Ritorno: ${shortDay(trip.inbound.segments[0].departure)} ${trip.inbound.segments[0].departure.slice(11, 16)}` : '',
      primary ?? '',
    ].filter(Boolean);
    Share.share({ message: lines.join('\n') }).catch(() => undefined);
  };

  return (
    <ScrollView style={s.screen} contentContainerStyle={{ padding: space(4), gap: space(4), paddingBottom: insets.bottom + space(8) }}>
      <View>
        <Text style={s.title}>
          {city(origin)} → {flag(dest)} {city(dest)}
        </Text>
        <Hint>
          {round ? countLabel(trip.nights ?? 0, 'notte', 'notti') : 'Solo andata'}
          {round && trip.useful_minutes != null ? ` · ${duration(trip.useful_minutes)} ${dest === away ? 'a destinazione' : 'tra i voli'}` : ''}
          {round && home && home !== origin ? ` · rientro a ${city(home)}` : ''}
        </Hint>
      </View>
      <Card style={{ gap: space(1) }}>
        <Text style={{ color: c.text, fontSize: 30, fontWeight: '800' }}>{money(trip.price)}</Text>
        <Hint>
          {round ? 'andata e ritorno, due biglietti separati' : 'solo andata'}
          {trip.outbound.travelers > 1 ? `, ${trip.outbound.travelers} persone` : ''}
        </Hint>
        <Hint tone={trip.needs_recheck ? 'warn' : 'muted'}>{trip.needs_recheck ? 'Prezzo da aggiornare' : `Prezzo delle ${checked}`}</Hint>
      </Card>
      {feedback ? <Notice tone={feedback.tone}>{feedback.text}</Notice> : null}
      <Card>
        <Leg quote={trip.outbound} title="Andata" />
        {trip.inbound ? <Leg quote={trip.inbound} title="Ritorno" /> : null}
      </Card>
      {round && away && dest !== away ? (
        <Notice>
          Arrivi a {airportName(dest)} e riparti da {airportName(away)}: il trasferimento è a carico tuo.
        </Notice>
      ) : null}
      {trip.round_trip_from ? (
        <Notice tone="info">
          Con questa andata Google vende un biglietto A/R unico da {money(trip.round_trip_from)}, ma con un ritorno che può essere diverso da questo.
        </Notice>
      ) : null}
      <Button title="Vedi offerta su Google Flights" onPress={() => open(primary)} disabled={!primary} />
      {round && trip.inbound ? (
        <View style={s.row}>
          <Button small kind="secondary" title="Solo andata" onPress={() => open(safeLink(trip.outbound.booking_url))} style={{ flex: 1 }} />
          <Button small kind="secondary" title="Solo ritorno" onPress={() => open(safeLink(trip.inbound!.booking_url))} style={{ flex: 1 }} />
        </View>
      ) : null}
      <View style={s.row}>
        <Button kind="secondary" title="Aggiorna prezzo" onPress={recheck} loading={busy} style={{ flex: 1 }} />
        <Button kind="secondary" title="Condividi" onPress={share} style={{ flex: 1 }} />
      </View>
      <Hint>
        Dati di shopping osservati su Google Flights: numero volo, aeroporti, orari e prezzo. Non sono una conferma della compagnia. Condizioni e bagagli
        vanno verificati dal venditore.
      </Hint>
    </ScrollView>
  );
}
