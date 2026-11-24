# Flight Finder per Android e iPhone

La ricerca voli del portale [flight-finder](https://github.com/gzanee/flight-finder)
come app nativa, scritta in **Kotlin Multiplatform** con **Compose Multiplatform**:
lo stesso codice Kotlin, interfaccia compresa, gira su Android e su iPhone.
Nessun server: le richieste a Google Flights partono dalla connessione del
telefono. Il motore è un porting del portale Python, verificato contro di
esso caso per caso (vedi *Test*).

## Com'è fatto

```
engine/       motore di ricerca, Kotlin puro (nessuna UI, nessuna dipendenza Android)
  Query.kt      richiesta tfs (protobuf) come fast-flights, parametri di LiveQuotes
  Decode.kt     lettura della pagina Google (decode + fli.parse_flight_row)
  Live.kt       rete: pausa fra le richieste, cache, retry, blocchi, consenso cookie
  Flexible.kt   richiesta, coppie di date, verifica dei voli, piano (flexible.py)
  Jobs.kt       ricerche riprendibili (flexible_jobs.py)
  Results.kt    filtri, gruppi, suggerimenti, calendario (result_browser.py)
  Airports.kt   catalogo aeroporti offline, autocomplete, paesi
  Calendar.kt   date e orari locali degli aeroporti, cambio dell'ora
app/          schermate in Compose Multiplatform, comuni ad Android e iOS
  commonMain/   interfaccia, modulo di ricerca, salvataggio, rete (Ktor)
  androidMain/  rete OkHttp, cartella dell'app, schermo acceso, condivisione
  iosMain/      rete NSURLSession, cartella Documenti, schermo acceso, condivisione
  desktopMain/  solo per sviluppo: l'app su computer con un Google dimostrativo
androidApp/   l'app Android (MainActivity)
iosApp/       il progetto Xcode per iPhone
data/catalog  aeroporti, compagnie, rotte e paesi, esportati dal portale
data/fixtures casi di riferimento generati dal portale per i test
tools/        script che rigenerano data/ dal portale Python
```

## Android Studio

Apri la cartella del progetto (quella con `settings.gradle.kts`) con
**Android Studio → Open**. Al primo avvio scarica Gradle, Kotlin e l'Android
SDK. Poi scegli la configurazione **androidApp** e premi Run, su emulatore o
sul telefono collegato via cavo (con il debug USB attivo).

APK da installare direttamente, firmato con la chiave di debug:

```bash
./gradlew :androidApp:assembleRelease
```

Il file è in `androidApp/build/outputs/apk/release/`.

## iPhone (serve un Mac con Xcode)

Apri `iosApp/iosApp.xcodeproj` in Xcode. In `iosApp/Configuration/Config.xcconfig`
metti il tuo `TEAM_ID` (Xcode → Settings → Accounts), collega l'iPhone e premi
Run: Xcode chiama Gradle per compilare la parte Kotlin. Con un Apple ID
gratuito l'app scade dopo 7 giorni e va reinstallata; con l'Apple Developer
Program (99 €/anno) dura un anno. Con il plugin *Kotlin Multiplatform* di
Android Studio si può avviare l'iPhone anche da lì.

L'App Store e il Play Store probabilmente rifiuterebbero un'app che legge le
pagine di Google Flights: è pensata per uso personale.

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

Prima di una ricerca lunga apri **Prova la connessione a Google** in fondo
alla prima pagina: fa una sola ricerca reale e mostra cosa ha risposto Google.

Rispetto al portale mancano il PDF (c'è **Condividi** sul singolo viaggio),
l'aggiornamento delle rotte da Wikipedia (l'app usa lo snapshot del portale)
e i loghi delle compagnie.

## Limiti da conoscere

- **iPhone e ricerche lunghe**: iOS sospende le app in background. Durante una
  ricerca lo schermo resta acceso; se esci dall'app la ricerca si ferma e
  riparte con **Riprendi**, senza perdere i viaggi già trovati.
- **Blocchi di Google**: come nel portale, un 403/429 ferma la ricerca e
  impone 15 minuti di pausa. Su reti aziendali o VPN Google blocca più
  facilmente; i dati mobili di solito vanno bene.
- **Consenso cookie**: l'app manda il cookie di consenso "Rifiuta tutto"
  (SOCS) e, se Google chiede di nuovo il consenso, rifà l'handshake come
  `flightcore/consent.py`.

## Test

```bash
./gradlew :engine:jvmTest     # motore contro i casi del portale Python
./gradlew :app:desktopTest    # l'app intera guidata come da una persona
```

Il motore è verificato contro il portale su casi generati dal portale stesso
(`tools/golden_*.py`, già in `data/fixtures`):

- 989 richieste tfs identiche byte per byte;
- 222 pagine Google sintetiche con difetti e casi limite (orari ambigui al
  cambio dell'ora, compagnie e aeroporti sconosciuti, valute, involucri vuoti
  o rotti) e una pagina reale catturata da una connessione di casa;
- 8 ricerche complete contro un Google finto: stesse richieste, stessi
  viaggi, stessi prezzi, stesse viste dei risultati;
- 300 insiemi casuali di viaggi per filtri, ordinamenti e suggerimenti;
- validazione delle richieste (63 casi, messaggi d'errore compresi),
  autocomplete e paesi;
- comportamento di rete: blocchi, ripetizioni, consenso, cache, Ferma e
  Riprendi, ricontrollo del prezzo.

`:app:desktopTest` apre l'app su un Google dimostrativo, aggiunge aeroporti e
un paese, avvia una ricerca, apre risultati, filtri, un viaggio e lo storico,
e salva uno screenshot di ogni schermata in `app/build/screenshots`.

Per vedere l'app sul computer: `./gradlew :app:run -Dflightfinder.demo=true`
(senza `demo` usa la rete vera).

Se cambi il motore del portale: `bash tools/golden.sh` rigenera i casi
(cerca il portale con il suo `.venv` in `../flight-finder`, oppure dove indica
`FLIGHT_FINDER_PORTAL`; con `dati` riesporta anche il catalogo), poi porta la
stessa modifica in `engine/`: i test diranno dove i due divergono.
