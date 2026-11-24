/** One real search to check that Google answers this phone, with what came back. */
import { useState } from 'react';
import { ScrollView, Text } from 'react-native';
import { decode } from '../engine/decode';
import { parseRequest } from '../engine/flexible';
import { bookingUrl, oneWayQuery } from '../engine/google';
import { addDays, today } from '../engine/time';
import { money } from '../app-core/format';
import { live } from '../app-core/store';
import { Button, Card, Hint, Notice, styles } from '../ui/kit';
import { space, useColors } from '../ui/theme';

export default function Diagnostics() {
  const c = useColors();
  const s = styles(c);
  const [busy, setBusy] = useState(false);
  const [lines, setLines] = useState<string[]>([]);
  const [ok, setOk] = useState<boolean | null>(null);

  const run = async () => {
    setBusy(true);
    setOk(null);
    const log: string[] = [];
    const day = addDays(today(), 30);
    try {
      const r = parseRequest({ origins: ['VCE'], destinations: ['BCN'], trip_type: 'one_way', date_mode: 'fixed', start_date: day, departure_min: '00:00', max_stops: 1 });
      const q = oneWayQuery(['VCE'], ['BCN'], day, r);
      const started = Date.now();
      const html = await live.fetchPage(q);
      log.push(`Risposta in ${Date.now() - started} ms, ${Math.round(html.length / 1024)} kB.`);
      const [quotes, rejected] = decode(html, r, bookingUrl(q), [['VCE'], ['BCN']]);
      log.push(`Venezia → Barcellona, ${day}: ${quotes.length} voli letti, ${rejected} righe non verificabili.`);
      for (const quote of quotes.slice(0, 5)) {
        const seg = quote.segments[0];
        log.push(`${seg.airline_code} ${seg.flight_number} alle ${seg.departure.slice(11, 16)}: ${money(quote.price)}`);
      }
      setOk(true);
    } catch (exc) {
      log.push(`${(exc as Error).name}: ${(exc as Error).message}`);
      setOk(false);
    } finally {
      setLines(log);
      setBusy(false);
    }
  };

  return (
    <ScrollView style={s.screen} contentContainerStyle={{ padding: space(4), gap: space(4) }}>
      <Hint>
        Una sola ricerca reale, Venezia → Barcellona tra 30 giorni, dalla connessione di questo telefono. Serve a capire se Google risponde prima di
        avviare ricerche lunghe.
      </Hint>
      <Button title="Prova adesso" onPress={run} loading={busy} />
      {ok === true ? <Notice tone="good">Google risponde: le ricerche funzionano da questa rete.</Notice> : null}
      {ok === false ? (
        <Notice tone="bad">
          La prova non è riuscita. Se Google limita le richieste, aspetta un quarto d’ora o prova con un’altra rete (Wi-Fi o dati mobili).
        </Notice>
      ) : null}
      {lines.length ? (
        <Card style={{ gap: space(1.5) }}>
          {lines.map((l) => (
            <Text key={l} style={{ color: c.text, fontSize: 13, fontFamily: 'Menlo' }}>
              {l}
            </Text>
          ))}
        </Card>
      ) : null}
    </ScrollView>
  );
}
