# Design

Linguaggio visivo dell'app, ispirato a TREK (interfaccia di viaggio editoriale, superfici satinate, navigazione a capsule).

| File | Contenuto |
| --- | --- |
| `01_ANALISI_DESIGN.md` | Analisi di TREK mobile e web: colori, font, materiali, forme, applicazione a Flight Finder e a Compose |
| `02_CLAUDE_BRIEF.md` | Brief operativo per il lavoro sull'interfaccia |
| `03_TOKENS.json` | Valori verificati nel codice di TREK, distinti dalle proposte |

Le immagini di riferimento (showcase ufficiali di TREK) e la tavola visiva HTML restano nel kit originale e non sono incluse qui: contengono fotografie e marchi di terzi.

## Dove vivono i token nell'app

- `ui/theme/Color.kt`, `Theme.kt`: tela calda con sfumatura radiale, card, superficie annidata (più chiara della card, con bordo di luce), tinta d'inchiostro per chip e binari, vetro della barra e del dock, elemento attivo pieno, colori di stato.
- `ui/theme/Type.kt`: Poppins per titoli e testo, Geist 500 per etichette e metadati, cifre tabellari per orari e prezzi.
- `ui/screens/components/FlightFinderCard.kt`: forme 26 (card), 18 (annidate), 12 (dettagli).
- `ui/screens/components/FlightFinderScaffold.kt`: barra in alto e dock a pillola, bottoni tondi da 38 dp con area di tocco di 48 dp.
