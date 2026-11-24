"""Casi di riferimento per l'encoder TS: tfs prodotto dal motore Python."""
import os
from pathlib import Path
import sys
# Il portale Python (repo flight-finder): accanto a questo progetto, o dove dice FLIGHT_FINDER_PORTAL.
PORTAL = Path(os.environ.get("FLIGHT_FINDER_PORTAL", Path(__file__).resolve().parents[2] / "flight-finder"))
sys.path.insert(0, str(PORTAL))

import json, sys
from itertools import product
from pathlib import Path
from flightcore.flexible import FlexibleRequest
from flightcore.live_quotes import LiveQuotes, AirportGroupQuery

cases = []
for i, (adults, children, infants, seat, stops, bag, st, basic, airlines, dep, groups) in enumerate(product(
    [1, 2], [0, 1], [0, 1], ["economy", "business"], [0, 1, 3], ["base", "cabin", "checked"],
    [True, False], [False, True], [[], ["FR", "U2"]], [("00:00", "23:59"), ("18:00", "19:59"), ("06:30", "12:00")],
    [(("VCE",), ("BCN",)), (("VCE", "TSF"), ("BCN", "MAD", "LIS"))])):
    if i % 7:  # campione: tutte le opzioni compaiono, non tutte le combinazioni
        continue
    if infants > adults:
        continue
    spec = {"origins": list(groups[0]), "destinations": list(groups[1]), "trip_type": "one_way",
            "date_mode": "fixed", "start_date": "2026-12-24", "end_date": "2026-12-24",
            "adults": adults, "children": children, "infants": infants, "seat": seat, "max_stops": stops,
            "baggage": bag, "allow_self_transfer": st, "exclude_basic_economy": basic, "airlines": airlines,
            "departure_min": dep[0], "departure_max": dep[1]}
    req = FlexibleRequest.parse(spec)
    o, d = groups
    leg = LiveQuotes._leg(o[0], d[0], "2026-12-24", req, False)
    q = LiveQuotes._query(req, [leg], "one-way")
    if len(o) > 1 or len(d) > 1:
        q = AirportGroupQuery(q, o, d)
    cases.append({"request": {k: getattr(req, k) if not isinstance(getattr(req, k), tuple) else list(getattr(req, k)) for k in
                  ["adults", "children", "infants", "seat", "max_stops", "baggage", "allow_self_transfer", "exclude_basic_economy", "airlines", "departure_min", "departure_max", "currency"]},
                  "origins": o, "destinations": d, "day": "2026-12-24", "trip": "one-way", "tfs": q.params()["tfs"]})
# Andata e ritorno su biglietto unico
req = FlexibleRequest.parse({"origins": ["VCE"], "destinations": ["BCN"], "trip_type": "round_trip", "date_mode": "fixed",
    "start_date": "2026-12-24", "end_date": "2026-12-27", "return_min": "18:00", "return_max": "23:59"})
legs = [LiveQuotes._leg("VCE", "BCN", "2026-12-24", req, False), LiveQuotes._leg("BCN", "VCE", "2026-12-27", req, True)]
q = LiveQuotes._query(req, legs, "round-trip")
cases.append({"request": {k: getattr(req, k) if not isinstance(getattr(req, k), tuple) else list(getattr(req, k)) for k in
              ["adults", "children", "infants", "seat", "max_stops", "baggage", "allow_self_transfer", "exclude_basic_economy", "airlines", "departure_min", "departure_max", "return_min", "return_max", "currency"]},
              "origins": ["VCE"], "destinations": ["BCN"], "day": "2026-12-24", "back": "2026-12-27", "trip": "round-trip", "tfs": q.params()["tfs"]})
print(json.dumps(cases, indent=1))
