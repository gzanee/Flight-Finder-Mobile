import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useColors } from '../ui/theme';

export default function Layout() {
  const c = useColors();
  return (
    <>
      <StatusBar style="auto" />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: c.card },
          headerTintColor: c.primary,
          headerTitleStyle: { color: c.text, fontWeight: '700' },
          contentStyle: { backgroundColor: c.bg },
          headerBackTitle: 'Indietro',
        }}
      >
        <Stack.Screen name="index" options={{ title: 'Flight Finder' }} />
        <Stack.Screen name="place" options={{ presentation: 'modal', title: 'Aggiungi aeroporto' }} />
        <Stack.Screen name="search/[id]" options={{ title: 'Ricerca' }} />
        <Stack.Screen name="trip" options={{ presentation: 'modal', title: 'Viaggio' }} />
        <Stack.Screen name="history" options={{ title: 'Le tue ricerche' }} />
        <Stack.Screen name="diagnostics" options={{ title: 'Prova connessione' }} />
      </Stack>
    </>
  );
}
