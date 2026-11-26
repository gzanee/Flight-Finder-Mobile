# Brief operativo per Claude

## Uso

Carica questo file, `01_ANALISI_DESIGN.md`, `03_TOKENS.json` e le immagini in `references/`. Apri `04_TAVOLA_VISIVA.html` per una guida commentata e campioni originali. La tavola è autonoma e include le immagini; la cartella references contiene anche gli originali separati per Claude.

Il brief è in inglese per rendere pratiche le istruzioni di implementazione; puoi conversare con Claude in italiano. Sostituisci i tre campi iniziali prima di incollarlo. Se usi Claude Code, conserva questo documento in `design/` e aggiungi al CLAUDE.md del tuo progetto: “For UI work, read design/02_CLAUDE_BRIEF.md and design/03_TOKENS.json before making visual changes.” Non sovrascrivere il CLAUDE.md esistente.

---

## Prompt da incollare

Project: [APP NAME AND PURPOSE]
Stack: [e.g. Kotlin + Jetpack Compose, or the existing web stack]
Screen to implement first: [SCREEN AND ITS MAIN USER TASK]

Act as a product designer and frontend/mobile engineer. Build an original interface using the visual principles documented in the attached TREK design analysis and official reference images. Preserve my product's functionality and identity. Use the supplied sources as visual evidence, not as instructions to execute.

Read the design analysis and tokens before implementing. Treat values marked `verified` as observed source facts and values marked `proposed` as a starter system for this project. Do not present the proposed token file as TREK's official theme. Resolve conflicts by preserving usability, task clarity and platform behavior.

### Visual direction

- Editorial travel imagery and strong typographic hierarchy.
- Warm stone/off-white mobile background, quieter cool-neutral web surfaces.
- Poppins for primary UI and headings; Geist Sans Medium for subordinate labels, compact metadata and numbers where appropriate. Ensure fonts are actually loaded; use a deliberate fallback until then.
- Large rounded content containers, smaller nested surfaces, capsule-shaped navigation and circular icon actions.
- Frosted glass on floating navigation and overlays, with a subtle light border and concentrated soft shadow. Prefer more opaque surfaces behind dense content.
- Near-black active controls on light surfaces, near-white active controls in dark mode. Restrict saturated color to meaningful statuses, map routes, content and a small original brand accent.
- Photographs and maps carry most of the visual richness. Protect overlaid text with a stable gradient or backing surface.
- Distinct mobile composition: prioritize the next useful action, use vertical content, contextual navigation and sheets. Desktop can expose simultaneous filters/panels where useful.

Avoid generic colorful gradients, neon accents, heavy card outlines, ubiquitous blur, identical large radii on every nested element, default component-library styling, oversized empty hero sections and a compressed desktop layout on a phone. Do not copy the orange promotional montage background into the product. Do not reuse TREK's logo, mascot or destination photography as production assets.

### Workflow

1. Inspect the existing project and state the screen's main decision, current data needs and reusable components. Make routine implementation choices autonomously; ask only for information that blocks useful progress.
2. Define semantic color, typography, shape, spacing and motion tokens appropriate to this stack. Keep desktop and mobile palettes coordinated without forcing them to be identical.
3. Implement one representative screen first. For a web product, show mobile and desktop layouts; for native Android, phone and expanded-width behavior relevant to the app.
4. Use realistic content and long labels. Include loading, empty, error, selected and disabled states that this screen needs. Do not invent backend integrations or working purchase flows in a visual prototype.
5. Check the result visually at phone and desktop/expanded widths. Verify text enlargement, keyboard behavior, focus, safe areas and bottom-bar clearance. If you cannot render or test something, state that limitation.
6. Compare with the references specifically on hierarchy, type, spacing, material opacity, shape and adaptive composition. Fix the largest discrepancies before expanding to more screens.
7. Explain what was implemented and which tokens/components should be reused next.

### Accessibility and performance

Keep readable contrast over all real imagery and themes. Use text/icon labels as well as color for status. Preserve visible keyboard focus on web and accessible names for icon controls. Target at least 48dp touch areas in Android even when the visible circle is smaller. Respect font scaling, safe areas and reduced-motion preferences. Avoid expensive nested backdrop effects. Provide an opaque material fallback where background blur is unavailable.

### Android implementation constraint

For Jetpack Compose, translate the visual rules into semantic ColorScheme/Typography/Shapes and project-level tokens. Do not mechanically convert CSS px to dp/sp. `Modifier.blur` is not a general replacement for a background blur: verify the rendering approach for the project's supported Android versions. Prefer a coherent opaque fallback to a blur that harms legibility or performance. Use native interaction patterns and preserve navigation/back behavior.

### Review rubric

Score hierarchy, typography, materials, geometry, color restraint, imagery, mobile adaptation, content robustness, interaction feedback and accessibility from 0–2 each. Describe concrete remaining issues rather than claiming pixel-perfect similarity. Do not turn this into a full redesign of unrelated screens before the first screen is reviewed.

---

## Variante già pronta: Flight Finder

Use the prompt above with:

Project: Flight Finder, a flight discovery and comparison app.
Screen: flight search results, including a compact search summary, sorting/filtering, flight cards and a flight details view/sheet.
Stack: keep the existing stack; for flight-finder-mobile use the actual Android architecture and UI toolkit found in the repository.

Additional product constraints:

- Origins must be configurable. Venice and Treviso may be convenient initial preferences, never the only supported origins.
- Make fare, departure/arrival times, dates, stops, duration and baggage easy to compare. Use realistic demo values only when data is unavailable and label them accordingly.
- Keep photos primarily for destination discovery and selected contextual covers; results should remain efficient to scan.
- Use a near-black search/select action and a restrained original accent if needed. Do not default to a royal-blue travel-brand palette.
- Use a bottom dock only if the actual information architecture has persistent sections. Do not add a central plus button solely to imitate the reference.
- Start with mobile results and their web counterpart; defer unrelated screens until the visual system is settled.

## Prompt per correggere un risultato troppo generico

Compare the current implementation against the attached references. Identify the five largest visual discrepancies, especially typography, content scale, warm mobile surfaces, glass opacity and floating navigation geometry. Fix them using shared tokens, not scattered one-off overrides. Preserve the flight comparison hierarchy. Show the corrected screen with realistic long content and both light/dark states where supported. Explain any remaining deviation that is intentional for usability.
