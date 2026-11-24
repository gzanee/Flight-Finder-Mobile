"""Esporta per l'app i dati che il motore Python legge da librerie e file.

Uso: tools/golden.sh dati  (oppure con il Python del portale: python tools/export_data.py)
Scrive data/catalog/*.json. Rilanciare dopo aver aggiornato
airportsdata, fli o i file in flightcore/data.
"""
import json
import sys
from pathlib import Path

import os
APP = Path(__file__).resolve().parents[1]
ROOT = Path(os.environ.get("FLIGHT_FINDER_PORTAL", APP.parent / "flight-finder"))
sys.path.insert(0, str(ROOT))
import airportsdata  # noqa: E402
from fli.models import Airline, Airport  # noqa: E402

from flightcore import airports  # noqa: E402

OUT = APP / "data" / "catalog"
OUT.mkdir(parents=True, exist_ok=True)
meta = airportsdata.load("IATA")
catalog = airports._catalog()
italian = airports._italian_names()

# codice -> [nome catalogo, città, nome mostrato, paese, fuso, noto a fli]
rows = {}
for code in sorted(set(catalog) | set(meta)):
    info = meta.get(code, {})
    d = airports.details(code) if code in catalog else None
    rows[code] = [
        catalog.get(code, ""),
        d["city"] if d else "",
        d["name"] if d else "",
        info.get("country", ""),
        info.get("tz", ""),
        1 if code in Airport.__members__ else 0,
    ]
airlines = sorted(code.lstrip("_") for code in Airline.__members__)
(OUT / "airports.json").write_text(json.dumps(rows, ensure_ascii=False, separators=(",", ":")))
(OUT / "airlines.json").write_text(json.dumps(airlines, separators=(",", ":")))
(OUT / "routes.json").write_text((ROOT / "flightcore" / "data" / "routes.json").read_text())
(OUT / "places.json").write_text(json.dumps({
    "home": airports.HOME_AIRPORTS,
    "sets": {k: {"label": label, "codes": codes} for k, (label, codes) in airports.DESTINATION_SETS.items()},
    "aliases": airports.CITY_ALIASES,
    "main": {k: v.split() for k, v in airports.MAIN_AIRPORTS.items()},
}, ensure_ascii=False, indent=1))
print(len(rows), "aeroporti,", len(airlines), "compagnie")
