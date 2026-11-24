/** Searches saved on this phone. */
import { router, useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { Alert, FlatList, Pressable, Text, View } from 'react-native';
import { city, countLabel, money, shortDay } from '../app-core/format';
import { jobs, savedSearches } from '../app-core/store';
import { Hint, styles, tap } from '../ui/kit';
import { radius, space, useColors } from '../ui/theme';

const LABELS: Record<string, string> = {
  running: 'In corso',
  cancelling: 'In chiusura',
  cancelled: 'Fermata',
  blocked: 'In pausa',
  interrupted: 'Interrotta',
  complete: 'Completata',
};

export default function History() {
  const c = useColors();
  const s = styles(c);
  const [items, setItems] = useState<Awaited<ReturnType<typeof savedSearches>> | null>(null);
  const load = useCallback(() => {
    savedSearches().then(setItems, () => setItems([]));
  }, []);
  useFocusEffect(load);

  const remove = (id: string) =>
    Alert.alert('Eliminare la ricerca?', 'I risultati salvati di questa ricerca verranno cancellati dal telefono.', [
      { text: 'Annulla', style: 'cancel' },
      {
        text: 'Elimina',
        style: 'destructive',
        onPress: () =>
          jobs
            .remove(id)
            .then(load)
            .catch((e) => Alert.alert('Impossibile eliminare', e.message)),
      },
    ]);

  return (
    <FlatList
      style={s.screen}
      contentContainerStyle={{ padding: space(4), gap: space(3) }}
      data={items ?? []}
      keyExtractor={(i) => i.id}
      ListEmptyComponent={items ? <Hint>Nessuna ricerca salvata. Le ricerche restano su questo telefono finché non le elimini.</Hint> : undefined}
      renderItem={({ item }) => {
        const r = item.request;
        return (
          <Pressable
            accessibilityRole="button"
            onPress={() => {
              tap();
              router.push(`/search/${item.id}`);
            }}
            onLongPress={() => remove(item.id)}
            style={({ pressed }) => ({
              backgroundColor: c.card,
              borderRadius: radius.l,
              padding: space(4),
              borderWidth: 1,
              borderColor: c.border,
              opacity: pressed ? 0.8 : 1,
              gap: 2,
            })}
          >
            <View style={{ flexDirection: 'row' }}>
              <Text style={{ color: c.text, fontSize: 16, fontWeight: '800', flex: 1 }} numberOfLines={1}>
                {r.origins.map(city).join(' + ')} → {r.destinations.length ? r.destinations.slice(0, 3).map(city).join(', ') + (r.destinations.length > 3 ? '…' : '') : 'Ovunque'}
              </Text>
              <Text style={{ color: item.status === 'complete' ? c.good : c.warn, fontSize: 12, fontWeight: '700' }}>{LABELS[item.status] ?? item.status}</Text>
            </View>
            <Text style={{ color: c.muted, fontSize: 13 }}>
              {shortDay(r.start_date)}
              {r.start_date === r.end_date ? '' : ` – ${shortDay(r.end_date)}`} · {r.trip_type === 'one_way' ? 'solo andata' : 'andata e ritorno'}
            </Text>
            <Text style={{ color: c.muted, fontSize: 13 }}>
              {countLabel(item.count, 'viaggio', 'viaggi')}
              {item.count ? ` · da ${money(item.cheapest)}` : ''}
            </Text>
          </Pressable>
        );
      }}
      ListFooterComponent={items?.length ? <Hint>Tieni premuto su una ricerca per eliminarla.</Hint> : undefined}
    />
  );
}
