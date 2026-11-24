/** Trip cards and legs, as on the web page. */
import { Image } from 'expo-image';
import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import type { Quote, Trip } from '../engine/flexible';
import { city, countLabel, duration, flag, localTime, money, shortDay } from '../app-core/format';
import { tap } from './kit';
import { radius, space, useColors } from './theme';

function AirlineMark({ code, name }: { code: string; name: string }) {
  const c = useColors();
  const [failed, setFailed] = useState(false);
  const logo = /^[A-Z0-9]{2}$/.test(code) && !failed;
  return (
    <View style={{ width: 32, height: 32, borderRadius: 8, backgroundColor: c.chip, alignItems: 'center', justifyContent: 'center', overflow: 'hidden' }}>
      {logo ? (
        <Image
          source={{ uri: `https://www.gstatic.com/flights/airline_logos/70px/${code}.png` }}
          style={{ width: 28, height: 28 }}
          onError={() => setFailed(true)}
          accessibilityIgnoresInvertColors
        />
      ) : (
        <Text style={{ color: c.muted, fontWeight: '800' }}>{name.slice(0, 1)}</Text>
      )}
    </View>
  );
}

export function Leg({ quote, title }: { quote: Quote; title: string }) {
  const c = useColors();
  const first = quote.segments[0];
  const last = quote.segments[quote.segments.length - 1];
  const stops = quote.segments.length - 1;
  const airlines = [...new Set(quote.segments.map((s) => s.airline_name || s.airline_code))].join(', ');
  const numbers = quote.segments.map((s) => `${s.airline_code} ${s.flight_number}`).join(' + ');
  const nextDay = last.arrival.slice(0, 10) !== first.departure.slice(0, 10);
  const via = quote.segments
    .slice(0, -1)
    .map((s) => city(s.to_code))
    .join(', ');
  return (
    <View style={{ flexDirection: 'row', gap: space(3), paddingVertical: space(2) }}>
      <AirlineMark code={first.airline_code} name={first.airline_name || first.airline_code} />
      <View style={{ flex: 1, gap: 2 }}>
        <Text style={{ color: c.muted, fontSize: 12, fontWeight: '600' }}>
          {title} · {shortDay(first.departure)}
        </Text>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: space(2) }}>
          <Text style={{ color: c.text, fontSize: 17, fontWeight: '800' }}>{localTime(first.departure)}</Text>
          <Text style={{ color: c.muted, fontSize: 13 }}>{first.from_code}</Text>
          <View style={{ flex: 1, alignItems: 'center' }}>
            <Text style={{ color: c.muted, fontSize: 11 }}>{duration(quote.duration_min)}</Text>
            <View style={{ height: 1, alignSelf: 'stretch', backgroundColor: c.border, marginVertical: 2 }} />
            <Text style={{ color: stops ? c.warn : c.good, fontSize: 11, fontWeight: '600' }} numberOfLines={1}>
              {stops ? `${countLabel(stops, 'scalo', 'scali')} · ${via}` : 'Diretto'}
            </Text>
          </View>
          <Text style={{ color: c.text, fontSize: 17, fontWeight: '800' }}>
            {localTime(last.arrival)}
            {nextDay ? <Text style={{ fontSize: 11 }}> +1</Text> : null}
          </Text>
          <Text style={{ color: c.muted, fontSize: 13 }}>{last.to_code}</Text>
        </View>
        <Text style={{ color: c.muted, fontSize: 12 }} numberOfLines={2}>
          {airlines} · {numbers}
          {stops && quote.self_transfer === true ? ' · cambio volo a carico tuo' : ''}
        </Text>
      </View>
    </View>
  );
}

export function tripSummary(trip: Trip) {
  const out = trip.outbound.segments;
  const origin = out[0].from_code;
  const dest = out[out.length - 1].to_code;
  const round = Boolean(trip.inbound);
  const away = trip.inbound ? trip.inbound.segments[0].from_code : null;
  const home = trip.inbound ? trip.inbound.segments[trip.inbound.segments.length - 1].to_code : null;
  return { origin, dest, round, away, home };
}

export function TripCard({ trip, onPress, highlighted }: { trip: Trip; onPress: () => void; highlighted?: boolean }) {
  const c = useColors();
  const { origin, dest, round, away, home } = tripSummary(trip);
  const people = trip.outbound.travelers;
  const tags = [
    round && trip.useful_minutes != null ? `${duration(trip.useful_minutes)} ${dest === away ? 'a destinazione' : 'tra i voli'}` : '',
    round ? '2 biglietti separati' : '',
    { cabin: 'Trolley incluso', checked: 'Valigia inclusa', base: '' }[trip.outbound.baggage],
    trip.round_trip_from ? `A/R unico da ${money(trip.round_trip_from)}` : '',
  ].filter(Boolean);
  return (
    <Pressable
      accessibilityRole="button"
      onPress={() => {
        tap();
        onPress();
      }}
      style={({ pressed }) => ({
        backgroundColor: c.card,
        borderRadius: radius.l,
        padding: space(4),
        borderWidth: highlighted ? 2 : 1,
        borderColor: highlighted ? c.primary : c.border,
        opacity: pressed ? 0.85 : 1,
        gap: space(1),
      })}
    >
      <View style={{ flexDirection: 'row', alignItems: 'flex-start', gap: space(2) }}>
        <View style={{ flex: 1 }}>
          <Text style={{ color: c.text, fontSize: 17, fontWeight: '800' }} numberOfLines={1}>
            {city(origin)} → {flag(dest)} {city(dest)}
          </Text>
          <Text style={{ color: c.muted, fontSize: 13 }}>
            {round ? countLabel(trip.nights ?? 0, 'notte', 'notti') : 'Solo andata'}
            {round && home !== origin && home ? ` · rientro a ${city(home)}` : ''}
          </Text>
        </View>
        <View style={{ alignItems: 'flex-end' }}>
          <Text style={{ color: c.text, fontSize: 20, fontWeight: '800' }}>{money(trip.price)}</Text>
          <Text style={{ color: trip.needs_recheck ? c.warn : c.muted, fontSize: 11 }}>
            {trip.needs_recheck ? 'da aggiornare' : people > 1 ? `${people} persone` : round ? 'A/R' : 'solo andata'}
          </Text>
        </View>
      </View>
      <Leg quote={trip.outbound} title="Andata" />
      {trip.inbound ? <Leg quote={trip.inbound} title="Ritorno" /> : null}
      {tags.length ? (
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: space(1.5) }}>
          {tags.map((t) => (
            <View key={t} style={{ backgroundColor: c.chip, borderRadius: 999, paddingHorizontal: space(2.5), paddingVertical: 3 }}>
              <Text style={{ color: c.muted, fontSize: 12, fontWeight: '600' }}>{t}</Text>
            </View>
          ))}
        </View>
      ) : null}
    </Pressable>
  );
}
