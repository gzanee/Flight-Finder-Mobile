# Flight Finder per Android

La ricerca voli del portale [flight-finder](https://github.com/gzanee/flight-finder)
come app Android nativa, in Kotlin con Jetpack Compose. Non c'è nessun server:
le richieste a Google Flights partono dalla connessione del telefono. Il motore
è il porting del portale Python, verificato contro di esso caso per caso (vedi
*Test*).

| Ricerca | Mete | Viaggi (tema scuro) | Viaggio | Storico |
|---|---|---|---|---|
| ![](docs/screenshots/1_ricerca.png) | ![](docs/screenshots/3_risultati_mete.png) | ![](docs/screenshots/4_risultati_viaggi.png) | ![](docs/screenshots/5_viaggio.png) | ![](docs/screenshots/6_storico.png) |

## Com'è fatto

L'impostazione è quella di Prix-Riordino: un solo modulo `:app`, Hilt,
Navigation Compose, una schermata per cartella con `XxxScreen` (collegata al
ViewModel) e `XxxScreenContent` (pura, con `@DevicePreview`).

```
it.gzanee.flightfinder
  domain/model        SearchRequest, Quote, Trip, Plan, SearchJob, ResultPage…
  domain/repository   interfacce (SearchRepository, AirportRepository, ThemeManager…)
  domain/search       il motore, Kotlin puro
    AirportCatalog      aeroporti, paesi, rotte, autocomplete
    Validation          controlli della richiesta (messaggi del portale)
    Planner             coppie di date, gruppi di aeroporti, piano (flexible.py)
    Matching            verifica dei voli e combinazione A/R
    ResultBrowser       filtri, gruppi, suggerimenti, calendario (result_browser.py)
    SearchRunner        ricerche riprendibili e ricontrollo (flexible_jobs.py)
  data/google         query tfs, lettura della pagina, client con pause/cache/blocchi, consenso
  data/storage        ricerche salvate su file, cookie di consenso in DataStore
  data/repository     implementazioni *Impl
  data/di             moduli Hilt
  ui/navigation       AppNavigation e sealed class Screen
  ui/screens          search, place, results, trip, history, diagnostics, components
  ui/theme            colori, tema chiaro/scuro, tipografia
data/catalog          aeroporti, compagnie, rotte e paesi esportati dal portale (asset dell'app)
data/fixtures         casi di riferimento generati dal portale per i test
tools/                script che rigenerano data/ dal portale Python
```

## Android Studio

Apri la cartella del progetto con **Android Studio → Open** e premi Run, su un
emulatore o sul telefono collegato via cavo con il debug USB attivo.

Per un APK da installare direttamente (firmato con la chiave di debug):

```bash
./gradlew :app:assembleRelease
```

Il file è in `app/build/outputs/apk/release/`.

Il Play Store probabilmente rifiuterebbe un'app che legge le pagine di Google
Flights, quindi è pensata per uso personale.

## Cosa fa

Fa tutto quello che fa il portale:

- un unico modulo con date esatte o flessibili, solo andata o andata e ritorno;
- fino a 10 origini e 200 destinazioni (anche un paese intero), oppure Ovunque;
- il preset Weekend e i filtri su orari, durate, scali, compagnie, bagagli e
  aeroporti del ritorno.

I risultati si esplorano per destinazione o tutti insieme. Ci sono:

- i tre suggerimenti: più economico, più tempo a destinazione, miglior compromesso;
- il calendario dei prezzi e i filtri;
- il ricontrollo del prezzo e il confronto con il biglietto A/R unico;
- il link all'offerta su Google Flights e la condivisione.

Le ricerche restano salvate sul telefono e si possono fermare e riprendere.
Mentre una ricerca gira, lo schermo resta acceso.

Prima di una ricerca lunga usa **Prova connessione**: fa una sola ricerca reale
e mostra cosa ha risposto Google.

Rispetto al portale mancano tre cose:

- il PDF (sul singolo viaggio c'è **Condividi**);
- l'aggiornamento delle rotte da Wikipedia (l'app usa lo snapshot del portale);
- i loghi delle compagnie.

## Limiti da conoscere

- **Uscire dall'app**: Android può fermare un'app in background. Se succede,
  la ricerca risulta "Interrotta" nello storico e riparte con **Riprendi**,
  senza perdere i viaggi già trovati.
- **Blocchi di Google**: come nel portale, un 403/429 ferma la ricerca e
  impone 15 minuti di pausa. Su reti aziendali o VPN Google blocca più
  facilmente; i dati mobili di solito vanno bene.
- **Consenso cookie**: l'app manda il cookie di consenso "Rifiuta tutto"
  (SOCS). Se Google chiede di nuovo il consenso, rifà l'handshake come
  `flightcore/consent.py`.

## Test

```bash
./gradlew :app:testDebugUnitTest
```

Sono test JUnit che confrontano il motore con il portale, su casi generati dal
portale stesso (`tools/golden_*.py`, già salvati in `data/fixtures`):

- le richieste tfs sono identiche byte per byte;
- le pagine Google sintetiche coprono difetti e casi limite: orari ambigui al
  cambio dell'ora, compagnie e aeroporti sconosciuti, valute, involucri vuoti
  o rotti. C'è anche una pagina reale catturata da una connessione di casa;
- 8 ricerche complete contro un Google finto danno le stesse richieste, gli
  stessi viaggi, gli stessi prezzi e le stesse viste dei risultati;
- 300 insiemi casuali di viaggi coprono filtri, ordinamenti e suggerimenti;
- validazione delle richieste (messaggi d'errore compresi), autocomplete e paesi;
- comportamento di rete: blocchi, ripetizioni, consenso, cache e ricontrollo.

Se cambi il motore del portale, lancia `bash tools/golden.sh` per rigenerare i
casi. Lo script cerca il portale con il suo `.venv` in `../flight-finder`,
oppure dove indica `FLIGHT_FINDER_PORTAL`; con l'argomento `dati` riesporta
anche il catalogo. Poi porta la stessa modifica in `domain/search`: i test
diranno dove i due divergono.
