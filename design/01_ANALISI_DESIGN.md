# TREK — anatomia di uno stile, mobile e web

Riferimento per Giacomo Zane · analisi del 26 novembre 2026.

## Come leggere questo documento

Questo kit serve a ricreare il linguaggio visivo di TREK in un prodotto originale. Le fonti sono il repository pubblico, i due showcase ufficiali e l'ispezione della demo su desktop (1280 × 720) e telefono (390 × 844). Ho visitato dashboard, planner e introduzione alla demo; ho letto i token desktop/mobile e i componenti della navigazione. Non è un audit di ogni schermata, né una prova dell'app installata su hardware Android/iOS. La vista mobile osservata è quella web della demo; il repository documenta un contenitore Capacitor/WebView che carica l'interfaccia dal server.

Distinzione delle evidenze:

- **Verificato nel codice**: valori espliciti nelle fonti elencate in fondo.
- **Osservato**: composizione visibile nella demo o nei materiali ufficiali.
- **Proposto**: scelta mia per rendere il linguaggio riutilizzabile nelle tue app. Non è una misura originale di TREK.

Il codice di main e la demo possono non essere alla stessa revisione. I valori centrali di font e palette mobile coincidono anche con gli stili calcolati della demo. Gli screenshot promozionali mostrano alcuni contenuti e layout diversi dalla demo attuale.

## 1. Come chiamare questo stile

La mia descrizione è **interfaccia di viaggio editoriale, con superfici in vetro satinato e navigazione a capsule**. In inglese, utile come direzione creativa: *editorial travel UI, frosted glass surfaces, floating pill navigation, restrained monochrome controls*.

Sono etichette descrittive, non il nome ufficiale di un sistema di design. “Glassmorphism” da solo è troppo generico: non comunica le fotografie dominanti, il contrasto tipografico, la palette calda del telefono e la forte organizzazione funzionale del planner. C'è una parentela visiva con interfacce Apple e dock flottanti; questo è un confronto estetico, non una dichiarazione sull'origine del progetto. Il vetro non mostra necessariamente gli effetti ottici di rifrazione di altri sistemi chiamati Liquid Glass.

L'effetto nasce da cinque scelte che lavorano insieme:

1. Il viaggio occupa la scena: fotografie ampie e mappe quasi a tutta superficie.
2. L'interfaccia si ritira in toni neutri: il colore arriva soprattutto dai contenuti.
3. I titoli hanno molta presenza, i metadati sono piccoli e ordinati.
4. Le azioni vivono in isole tondeggianti: capsule, cerchi, pannelli sospesi.
5. La densità cambia con il compito: dashboard emozionale, planner operativo.

## 2. Perché appare così curato

La gerarchia è immediata. Nella dashboard desktop, la fotografia del prossimo viaggio ha un titolo enorme in bianco e una fascia di dati traslucida. Prima riconosci il viaggio, poi leggi date, partecipanti e luoghi. Le schede statistiche e i widget laterali entrano in una seconda lettura.

Su mobile, la cover resta grande ma il titolo si sposta in un pannello scuro sovrapposto alla parte bassa della fotografia. Le informazioni diventano brevi chip su una riga. Non viene mantenuta la fascia desktop con quattro colonne: viene preservata la funzione, cambiando la composizione.

Il dettaglio decisivo è la variazione di scala: una grande immagine, un titolo forte, poche informazioni minori. Se ogni pannello avesse lo stesso peso, gli stessi numeri grandi e lo stesso bordo, l'effetto diventerebbe un cruscotto uniforme.

## 3. Colore: due famiglie coordinate

### Mobile — valori verificati

| Ruolo | Chiaro | Scuro |
| --- | --- | --- |
| Testo principale | `#101013` | `#F5F5F7` |
| Testo secondario | `#68686F` | `#9C9CA3` |
| Testo tenue | `#9A9AA1` | `#6E6E76` |
| Fondo di base | `#F3F2EF` | `#0A0A0C` |
| Vetro | bianco 60% | `rgb(26 26 31 / 60%)` |
| Dock | bianco 80% | `rgb(26 26 31 / 82%)` |
| Card | bianco 55% | `rgb(24 24 29 / 55%)` |
| Superficie interna | bianco 68% | bianco 7% |
| Bordo card | bianco 75% | bianco 10% |
| Azione attiva | `#101013` | `#F5F5F7` |
| Testo su azione | bianco | `#101013` |

Il fondo mobile non è semplicemente grigio. Il gradiente radiale verificato passa da `#FBFAF7` a `#F1EFEA` e `#E9E7E1`: dà una sensazione di carta chiara e pietra calda. La luce resta molto discreta. Lo sfondo arancione dei montaggi ufficiali appartiene alla presentazione promozionale: **non va trasformato nello sfondo dell'app**.

Le opacità sono parte del colore finale. Una card bianca al 55% assume una tonalità diversa in base al fondo; non equivale a un bianco solido. Il dock è più opaco delle card per restare leggibile quando le foto gli scorrono sotto.

### Desktop — base verificata

| Ruolo | Chiaro | Scuro |
| --- | --- | --- |
| Fondo principale | `#FFFFFF` | `#121215` |
| Fondo secondario | `#F8FAFC` | `#1A1A1E` |
| Card | `#FFFFFF` | `#131316` |
| Testo principale | `#111827` | `#F4F4F5` |
| Testo attenuato | `#6B7280` | `#A1A1AA` |
| Accento di base | `#111827` | `#E4E4E7` |
| Pannello laterale | `rgba(250,250,250,.82)` | `rgba(19,19,22,.82)` |

Il desktop è più freddo e operativo. Alcune sezioni, come Vacay e Journey, hanno token dedicati: TREK non è una sola ricetta identica applicata ovunque. Anche il codice contiene colori indaco e temi alternativi. Non bisogna concludere che l'intero prodotto sia rigorosamente privo di accenti colorati.

Per emulare lo stile, mantieni neutri i controlli principali. Usa colore per stato, categoria, percorso o un accento di marca circoscritto. Nel mobile gli stati verificati sono verde `#2FA37A`, ambra `#E8A13A`, informazione `#4A7DDB`, pericolo `#D6273B`.

## 4. Tipografia: il dettaglio che cambia la somiglianza

**Verificato:** il font principale è Poppins. Il font secondario è Geist Sans. Nei token globali, il secondo è destinato al testo subordinato; nella configurazione Tailwind è esplicitamente associato a numeri ed etichette mobile. Nel mobile, `.font-geist` viene portato a peso 500 anche quando altre utility chiedono un peso maggiore.

Poppins ha forme geometriche e un carattere amichevole. Il peso marcato dei titoli crea la sensazione da applicazione consumer curata. Geist tiene compatti numeri e metadati e riduce la pesantezza dei dettagli. Sostituire tutto con un solo font di sistema cambia sensibilmente il risultato.

I livelli semantici generali verificati sono titolo 24px/1.2, sottotitolo 18px/1.35, corpo 14px/1.5, caption 12px/1.4 e controllo 13px/1.3, con moltiplicatori per le preferenze dell'utente. Non sono i valori di ogni titolo: la hero ha una scala propria e i componenti mobile usano anche misure locali.

Scala proposta per un tuo sistema coerente:

| Funzione | Web | Mobile | Peso proposto |
| --- | --- | --- | --- |
| Titolo hero | 48–72px | 24–30sp | Poppins 700 |
| Titolo schermata | 28–32px | 24sp | Poppins 700 |
| Titolo card | 18–22px | 16–20sp | Poppins 600–700 |
| Corpo | 14–16px | 14–16sp | Poppins 400–500 |
| Metadati | 12–13px | 12–13sp | Geist 500 |
| Etichetta breve | 10–11px | 11–12sp | Geist 500, tracking lieve |

Questa scala è una proposta. Le etichette piccole di TREK aiutano la densità ma non vanno imitate sacrificando la leggibilità. Informazioni decisive come prezzo, orario, errore e pulsante devono reggere l'ingrandimento del testo. Uppercase solo per etichette brevi; non per paragrafi o descrizioni.

## 5. Forme, materiali e profondità

Si distinguono tre geometrie: grandi rettangoli arrotondati per contenuti, capsule per gruppi di controlli e cerchi per azioni a icona. I contenitori annidati hanno raggi più piccoli del contenitore esterno. Questa differenza evita l'effetto di una pila di pillole indistinguibili.

Misure verificate nei componenti mobile:

| Elemento | Misura originale |
| --- | --- |
| Top bar | raggio pieno; padding 14px orizzontale e 8px verticale |
| Top bar, materiale | blur 26px, saturazione 1.7, bordo bianco 75% |
| Top bar, ombra | `0 14px 34px -16px rgba(0,0,0,.22)` |
| Dock | altezza 62px, raggio 31px, lati a 16px |
| Dock, posizione | 12px sopra la safe area inferiore |
| Dock, materiale | blur 30px, saturazione 1.4 |
| Dock, ombra | `0 16px 44px -14px rgba(0,0,0,.35)` |
| Cerchio di navigazione | 42px |
| Azione centrale nel dock | 56px |
| Pulsante icona top bar | 38px, chiusura sheet 34px |
| Segmented control | padding pista 3px, pillola attiva piena |

Un'ombra con spread negativo resta concentrata vicino alla superficie: crea sospensione senza una cornice grigia pesante. Il bordo chiaro sottile simula il bordo illuminato del vetro. Il blur ha senso sopra fotografie e mappe; su un fondo uniforme produce poca differenza visibile.

Per le tue card propongo raggi 24–28px per contenitori principali, 16–20px per gruppi interni e 10–14px per dettagli. Sono misure di partenza, non un censimento dei raggi originali. I token desktop verificati includono 12, 16 e 20px, ma le grandi hero possono usare raggi locali diversi.

## 6. Desktop: composizione e navigazione

**Dashboard osservata:** barra superiore con logo a sinistra, navigazione raccolta al centro e account/utilità a destra. Hero ampia, statistiche modulari, elenco viaggi e widget di servizio. A 1280px la hero occupa gran parte della prima schermata; il montaggio promozionale usa una composizione più larga con colonna laterale evidente.

**Planner osservato:** mappa come piano di fondo; due pannelli laterali flottanti, con larghezza regolabile; barra di categorie sopra la mappa; tab di sezione sotto l'intestazione. Il pannello sinistro organizza giorni e tappe in una timeline, quello destro offre ricerca e aggiunta. È un ambiente di lavoro, quindi servono più controlli e più densità rispetto alla dashboard.

Regole da trasferire: gruppi di azioni vicine al contenuto su cui operano; mappa sempre riconoscibile; pannelli che non assorbano tutto il viewport; separazione fra navigazione del prodotto, sezioni del viaggio e filtri locali. Non trasportare questa complessità in una schermata che richiede una sola ricerca.

## 7. Mobile: una composizione autonoma

La dashboard mobile usa una barra alta flottante, cover grandi, informazioni sovrapposte e dock basso. La creazione è un'azione centrale più grande. Nel viaggio il dock è invece contestuale: le sue voci cambiano rispetto alla dashboard. Anche l'azione centrale, nel codice, cambia funzione in base alla sezione.

Nel planner mobile osservato, l'utente vede in alto Travel / Plan / Places, sotto i giorni in orizzontale e poi una card “Up next”. Seguono prenotazioni e tappe, con tempi di spostamento fra gli elementi. La mappa è una vista raggiungibile con un comando esplicito. La sequenza d'uso prevale sulla compresenza di tutti i pannelli desktop.

| Problema | Desktop | Telefono |
| --- | --- | --- |
| Navigare fra sezioni | barra superiore e tab | dock e controlli contestuali |
| Capire il prossimo viaggio | hero + fascia dati | cover + pannello sovrapposto + chip |
| Pianificare tappe | mappa e pannelli simultanei | sequenza verticale e cambio vista |
| Cambiare giorno | elenco e gruppi espandibili | strip orizzontale di giorni |
| Modificare dati | pannelli/dialoghi | sheet; il codice gestisce anche tastiera e safe area |
| Azioni secondarie | visibili/contestuali | raccolte in More quando necessario |

Non assumere un unico breakpoint universale: il repository usa anche classi di dimensione wide/split, input touch e gestione della tastiera. Per un nuovo progetto si possono partire da 768px e 1200px come soglie proposte, poi decidere in base al contenuto.

## 8. Icone, fotografia e mappe

Le icone di navigazione verificate provengono da Lucide. Nel dock hanno dimensione 21px e stroke 1.9; l'icona dashboard usa 18px/2.1. L'icona dentro il cerchio non cambia arbitrariamente peso quando selezionata: è il cerchio pieno a comunicare lo stato. Le fotografie rotonde di luogo e gli avatar aggiungono informazione e vita in mezzo ai dati.

Le immagini grandi hanno soggetto chiaro e zone utili per la sovrapposizione del testo. Crop e contrasto contano quanto la foto scelta. Usa una protezione scura dietro titoli bianchi; non affidarti alla foto fortunata dell'esempio. Su mobile la base scura satinata dà maggiore stabilità al titolo.

Le mappe osservate usano basi relativamente attenuate, percorsi ben visibili e marker con gerarchie diverse: fotografia, numero aggregato, categoria. Il codice mobile definisce colori di base, strade e acqua separati. Non è sufficiente mettere un pannello trasparente sopra una mappa molto satura: tutto competerebbe per l'attenzione.

## 9. Movimento e stati

Valori verificati nel CSS: pop-in 220ms; sheet in 260ms, out 220ms; sheet dal basso 420ms, chiusura 300ms; drawer 280ms. Curve principali `cubic-bezier(.23,1,.32,1)` e `cubic-bezier(.32,.72,0,1)`. Alcune transizioni di pagina usano 400ms in avanti e 340ms indietro. Questi valori sono letti nel codice, non misurati fotogramma per fotogramma nella demo.

La funzione del movimento è chiarire da dove arriva un pannello e quale vista sostituisce. Il segmented ha un indicatore scorrevole. Gli stati vuoti aggiungono una mascotte, che nello showcase e nella demo rende più amichevoli le aree prive di contenuti. Per le tue app usa un'illustrazione tua; il marchio di TREK non fa parte della grammatica da trasferire.

Per un'implementazione completa chiedi a Claude: stato normale, selezionato, premuto, hover web, focus tastiera, disabilitato, caricamento, vuoto, errore e offline dove pertinente. Non usare il solo colore per errori o stati. Supporta movimento ridotto; il CSS originale converte diverse animazioni in dissolvenze brevi.

## 10. Applicazione concreta a Flight Finder

Questa è una proposta originale, non una schermata esistente di TREK. Il tuo oggetto principale è un volo e la decisione principale è confrontare prezzo, orari e condizioni. Va mantenuta questa gerarchia anche prendendo in prestito l'atmosfera di viaggio.

**Ricerca mobile:** fondo caldo, barra compatta con logo tuo e profilo, card di ricerca chiara con origine e destinazione, date e passeggeri in gruppi leggibili. CTA “Cerca voli” scura. Una fotografia può introdurre una destinazione o un'ispirazione, ma non deve spingere il modulo sotto la prima schermata. Origini configurabili: Venezia e Treviso possono essere preferenze iniziali, non limiti del prodotto.

**Risultati mobile:** riepilogo di ricerca a capsula; filtri in uno strip; card volo con prezzo e orari forti, tratta leggibile, durata/scali in secondo piano e bagaglio esplicito. Preferiti in pulsante circolare. La fotografia è utile per esplorare destinazioni, molto meno in ogni riga di confronto. Un dock serve solo se ci sono vere sezioni ricorrenti, per esempio Esplora, Salvati e Avvisi; il “+” centrale non è obbligatorio e potrebbe essere meno adatto della ricerca.

**Risultati web:** filtri in colonna laterale e lista più larga di risultati, stessa famiglia tipografica, stessi materiali e forme. Prezzo e CTA allineati per confronto rapido. Pannello dettagli opzionale; mappa solo quando aiuta la scelta. Un'app non diventa più simile a TREK aggiungendo una mappa senza scopo.

**Dettaglio:** eventuale cover destinazione, riepilogo voli su superficie leggibile, timeline della tratta, prezzo e CTA facilmente raggiungibili. Sul telefono, una barra d'azione bassa deve rispettare safe area e non coprire condizioni o contenuti.

Per rendere tua la direzione, conserva neutri e materiali ma scegli fotografie, tono di voce e un accento originale. Una variante terracotta o verde profondo può essere esplorata solo in elementi limitati, verificando contrasto e riconoscibilità. Non è necessario usare il blu royal per comunicare aviazione.

## 11. Traduzione in Jetpack Compose

Il linguaggio è indipendente da React. Per Compose definisci colori semantici chiari/scuri, Typography con Poppins/Geist distribuiti localmente, Shape e misure condivise. Usa `RoundedCornerShape` per i pannelli, `CircleShape` per azioni, gradienti con `Brush`, immagini con crop controllato e inset da `WindowInsets` per dock/sheet.

Non affidarti ai colori dinamici Android se cambiano automaticamente la palette scelta. I componenti Material possono fornire comportamento e accessibilità, ma vanno adattati in forma, colore e tipografia. Una `NavigationBar` standard lasciata ai valori predefiniti non produce il dock mostrato.

Il vetro richiede attenzione: sfocare un composable non significa necessariamente sfocare ciò che sta dietro. Scegli una tecnica di backdrop adatta alle versioni Android del progetto e controlla prestazioni e fallback. Un pannello più opaco con bordo e ombra è un fallback coerente. Non aggiungere una libreria solo per replicare un effetto secondario prima di aver verificato il risultato.

Mantieni target touch almeno 48dp nella tua app, anche quando il cerchio disegnato è 38–42dp. Le misure px del web e dp/sp Android non sono equivalenti fisici da copiare alla cieca. Prova testo ingrandito, tastiera, schermo stretto e tema scuro.

## 12. Errori che farebbero perdere lo stile

- Descriverlo solo come “moderno e minimal”: lascia troppe decisioni arbitrarie.
- Applicare blur e trasparenza a tutte le superfici: la gerarchia dei materiali scompare.
- Usare lo stesso bianco e nero su mobile e desktop: si perde il calore mobile.
- Usare tutto Poppins bold: i metadati diventano rumorosi.
- Fare ogni box a capsula: card e controlli non si distinguono più.
- Aggiungere gradienti colorati grandi, bordi spessi e molte CTA piene sulla stessa schermata.
- Riprodurre il montaggio con telefoni e sfondo arancio come fosse il prodotto.
- Nascondere informazioni essenziali per mantenere la card piccola.
- Ridurre il desktop in scala sul telefono.
- Copiare i difetti dei riferimenti: etichette troppo tenui, controlli piccoli o titoli troncati possono essere migliorati.

## 13. Come lavorare con Claude

Usa insieme regole scritte e riferimenti visivi. Il file Markdown rende esplicite le decisioni; le immagini riducono l'ambiguità su proporzioni e composizione. Il CSS/JSON proposto nel kit serve come contratto condiviso, non come tema ufficiale TREK.

1. Crea una cartella `design/` nel tuo progetto con questa analisi, il brief e i token.
2. Allega le due immagini ufficiali come riferimenti, spiegando quali parti contano. Se la tua interfaccia Claude non accetta ZIP, estrailo e carica Markdown e immagini separatamente.
3. Specifica progetto, stack, schermata e vincoli funzionali. Per Claude Code puoi aggiungere al tuo `CLAUDE.md` una breve istruzione che rimanda a `design/02_CLAUDE_BRIEF.md`; non sostituire le istruzioni esistenti.
4. Chiedi prima una sola schermata rappresentativa in mobile e desktop. Per Flight Finder, la lista risultati è un buon banco di prova perché rivela subito gerarchia e densità.
5. Confronta il risultato con la tavola, correggi proporzioni e materiali, poi estendi i componenti alle altre pagine.
6. Verifica con dati reali, titoli lunghi, più aeroporti, prezzo assente, errori e caricamento. Una demo con tre card perfette non prova la qualità del sistema.

Non serve chiedere di costruire l'intera app in un solo prompt. La coerenza si ottiene fissando una base e riutilizzandola, con una verifica visiva a ogni nuova famiglia di schermate.

## 14. Checklist di revisione

Assegna 0, 1 o 2 punti a ogni criterio: assente, parziale, convincente. È una rubrica proposta, non una valutazione scientifica.

| Criterio | Cosa verificare |
| --- | --- |
| Gerarchia | primo sguardo sull'oggetto o sulla decisione principale |
| Tipografia | Poppins/Geist con ruoli distinti e pochi pesi |
| Materiali | vetro riservato a navigazione e overlay, superfici leggibili |
| Geometria | card, capsule e cerchi con funzioni riconoscibili |
| Colore | neutri dominanti; stati e accenti circoscritti |
| Immagini | crop intenzionale e testo sempre leggibile |
| Mobile | composizione e priorità proprie |
| Contenuti | dati lunghi e stati non ideali gestiti bene |
| Interazione | focus, pressione, selezione e feedback chiari |
| Accessibilità | testo scalabile, target adeguati e contrasto verificato |

Obiettivo suggerito: almeno 16/20 senza zero su contenuti, mobile o accessibilità. La somiglianza estetica non deve mascherare un confronto voli difficile.

## Fonti e perimetro

- [Repository e README](https://github.com/liketrek/TREK) — funzioni, accesso demo, riferimenti visivi.
- [Demo](https://demo.liketrek.com) — dashboard, planner, viste telefono osservate.
- [Showcase desktop originale](https://github.com/liketrek/TREK/blob/main/docs/screenshots/showcase-desktop-1.webp).
- [Showcase mobile originale](https://github.com/liketrek/TREK/blob/main/docs/screenshots/showcase-mobile.webp).
- [Token globali](https://github.com/liketrek/TREK/blob/main/client/src/index.css) — palette desktop, font, curve e livelli semantici.
- [Token mobile](https://github.com/liketrek/TREK/blob/main/client/src/mobile/mobile.css) — palette, materiali, motion, safe area.
- [Configurazione Tailwind](https://github.com/liketrek/TREK/blob/main/client/tailwind.config.js) — font Geist, type tiers, alias semantici.
- [MGlassBar](https://github.com/liketrek/TREK/blob/main/client/src/mobile/components/MGlassBar.tsx), [MBottomNav](https://github.com/liketrek/TREK/blob/main/client/src/mobile/components/MBottomNav.tsx), [MSegmented](https://github.com/liketrek/TREK/blob/main/client/src/mobile/components/MSegmented.tsx), [MIconBtn](https://github.com/liketrek/TREK/blob/main/client/src/mobile/components/MIconBtn.tsx) — geometrie, materiali, icone e stati.
- [Capacitor](https://github.com/liketrek/TREK/blob/main/client/capacitor.config.ts) — relazione fra contenitore installato e UI web.

I riferimenti visuali inclusi sono materiali originali di TREK, attribuiti qui e usati come esempi di analisi. Il kit non concede diritti su fotografie, logo o codice di terzi. Il percorso proposto è costruire componenti propri a partire dalle regole, mantenendo il proprio marchio. Per eventuale riuso di codice o asset consulta i file LICENSE, NOTICE e TRADEMARKS del progetto; questo documento non ne determina gli obblighi.
