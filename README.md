# Flight Finder per iPhone e Android

La ricerca voli del portale [flight-finder](https://github.com/gzanee/flight-finder),
in un'app che gira tutta sul telefono: nessun server, le richieste a Google
Flights partono dalla connessione del telefono. Fatta con Expo (React Native +
TypeScript). Il motore è un porting del portale Python, verificato contro di
esso caso per caso (vedi *Test*).

## Provarla subito (Expo Go)

Sul telefono installa **Expo Go** (App Store o Play Store). Sul Mac:

```bash
npm install
npx expo start
```

Inquadra il QR code con la fotocamera (iPhone) o con Expo Go (Android).
Telefono e Mac devono essere sulla stessa rete Wi-Fi; altrimenti usa
`npx expo start --tunnel`.

Prima di una ricerca lunga apri **Prova la connessione a Google** in fondo
alla pagina: fa una sola ricerca reale e mostra cosa ha risposto Google.

## Android Studio

Le cartelle `android/` e `ios/` non sono nel repo: Expo le genera dalla
configurazione (`app.json`), così restano allineate alle librerie.

```bash
npm install
npx expo prebuild -p android
```

Poi in Android Studio: **Open** → cartella `android/`. Da lì si avvia su
emulatore o telefono come qualsiasi progetto Android (prima avvia il bundler
con `npx expo start` in un terminale, per la build di debug). Le modifiche
vanno fatte in `src/` e `app.json`, non in `android/`: se cambi librerie o
configurazione, rigenera con `npx expo prebuild -p android --clean`.

Senza aprire Android Studio, `npm run android` compila e installa sul
telefono collegato (o sull'emulatore). Per iPhone, su Mac con Xcode,
`npx expo prebuild -p ios` e poi apri `ios/*.xcworkspace`, oppure `npm run ios`.

## Installarla come app vera

- **Android**: `npx eas-cli@latest build -p android --profile preview` produce
  un APK da installare direttamente (serve un account Expo gratuito).
  In alternativa, in locale: `npx expo run:android --variant release`, oppure
  da Android Studio *Build → Generate Signed App Bundle / APK*.
- **iPhone**: con Xcode e il telefono collegato via cavo,
  `npx expo run:ios --device --configuration Release`. Con un Apple ID
  gratuito l'app scade dopo 7 giorni e va reinstallata; con l'Apple
  Developer Program (99 €/anno) dura un anno, e si può usare anche
  `eas build -p ios`.

L'App Store e il Play Store probabilmente rifiuterebbero un'app che legge
le pagine di Google Flights: è pensata per uso personale.

## Cosa fa

Tutto quello che fa il portale: modulo unico con date esatte o flessibili,
solo andata o A/R, fino a 10 origini e 200 destinazioni (anche un paese
intero) oppure Ovunque, preset Weekend, filtri su orari, durate, scali,
compagnie, bagagli e aeroporti del ritorno. I risultati si esplorano per
destinazione o tutti insieme, con i tre suggerimenti (più economico, più
tempo a destinazione, miglior compromesso), il calendario dei prezzi, i
filtri, il ricontrollo del prezzo, il confronto con il biglietto A/R unico e
il link all'offerta su Google Flights. Le ricerche restano salvate sul
telefono e si possono fermare e riprendere.

Rispetto al portale mancano il PDF (c'è **Condividi** sul singolo viaggio) e
l'aggiornamento delle rotte da Wikipedia: l'app usa lo snapshot delle rotte
del portale, copiato in `src/engine/data/routes.json`.

## Limiti da conoscere

- **iPhone e ricerche lunghe**: iOS sospende le app in background. Durante una
  ricerca lo schermo resta acceso; se esci dall'app la ricerca si ferma e
  riparte con **Riprendi**, senza perdere i viaggi già trovati. Le ricerche
  con molti scali possono durare ore: i voli diretti arrivano per primi.
- **Blocchi di Google**: come nel portale, un 403/429 ferma la ricerca e
  impone 15 minuti di pausa. Sui dati mobili l'IP cambia spesso; su reti
  aziendali o VPN Google blocca più facilmente.
- **Consenso cookie**: l'app manda il cookie di consenso "Rifiuta tutto"
  (SOCS) e, se Google chiede di nuovo il consenso, rifà l'handshake come
  `flightcore/consent.py`.

## Com'è fatta

```
  src/engine/     motore di ricerca, porting 1:1 di flightcore/ del portale
    query.ts        richiesta tfs (protobuf) come fast-flights
    google.ts       parametri della richiesta (LiveQuotes._leg/_query)
    decode.ts       lettura della pagina Google (decode + fli.parse_flight_row)
    live.ts         rete: pausa fra le richieste, cache, retry, blocchi, consenso
    flexible.ts     richiesta, coppie di date, verifica dei voli, piano
    jobs.ts         ricerche riprendibili (flexible_jobs.py)
    results.ts      filtri, gruppi, suggerimenti, calendario (result_browser.py)
    airports.ts     catalogo aeroporti offline, autocomplete, paesi
    data/           dati esportati dal Python (tools/export_data.py)
  src/app-core/   modulo di ricerca, salvataggio su file, rete del telefono
  src/app/        schermate (expo-router)
  src/ui/         componenti
  tools/          esportazione dati e casi di riferimento dal portale Python
```

## Test: identico al motore Python

Il motore TypeScript è verificato contro quello Python su casi generati dal
portale stesso (`tools/golden_*.py`); i casi sono già nel repo, quindi i
test girano senza il portale:

- 989 richieste tfs identiche byte per byte;
- 222 pagine Google sintetiche con difetti e casi limite (orari ambigui al
  cambio dell'ora, compagnie e aeroporti sconosciuti, valute, involucri vuoti
  o rotti) e una pagina reale catturata da una connessione di casa;
- 8 ricerche complete contro un Google finto: stesse richieste, stessi
  viaggi, stessi prezzi, stesse viste dei risultati;
- 300 insiemi casuali di viaggi per filtri, ordinamenti e suggerimenti;
- validazione delle richieste (63 casi, messaggi d'errore compresi),
  autocomplete e paesi.

```bash
npm test            # vitest
npm run typecheck   # tsc
npm run golden      # rigenera i casi dal portale Python
```

`npm run golden` cerca il portale (con il suo `.venv`) in `../flight-finder`,
oppure dove indica `FLIGHT_FINDER_PORTAL`. `bash tools/golden.sh dati`
riesporta anche aeroporti, compagnie e rotte. Se cambi il motore del portale,
rigenera i casi e porta la stessa modifica in `src/engine/`: i test diranno
dove i due divergono.

Con `npx expo start --web` l'app gira anche nel browser, ma lì Google non è
raggiungibile (CORS): la versione web usa un Google dimostrativo
(`src/app-core/platform.web.ts`) ed è solo un'anteprima dell'interfaccia.
