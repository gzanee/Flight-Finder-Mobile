"""Validazione delle richieste, autocomplete aeroporti e paesi, dal Python."""
import os
from pathlib import Path
import sys
# Il portale Python (repo flight-finder): accanto a questo progetto, o dove dice FLIGHT_FINDER_PORTAL.
PORTAL = Path(os.environ.get("FLIGHT_FINDER_PORTAL", Path(__file__).resolve().parents[2] / "flight-finder"))
sys.path.insert(0, str(PORTAL))

import json, random, sys
from dataclasses import asdict
from datetime import date, timedelta
from pathlib import Path
from flightcore import airports
from flightcore.flexible import FlexibleRequest, SearchValidationError

TODAY = date.today()
rnd = random.Random(5)
d = lambda n: (TODAY + timedelta(days=n)).isoformat()
base = {"origins": ["VCE"], "destinations": ["BCN"], "start_date": d(20), "end_date": d(40), "departure_days": [4], "return_days": [6]}
variants = [
    {}, {"origins": "VCE, TSF"}, {"origins": []}, {"origins": ["XXX"]}, {"origins": ["vce", "VCE"]}, {"origins": [f"A{i}" for i in range(11)]},
    {"start_date": d(-1)}, {"end_date": d(10)}, {"end_date": d(400)}, {"start_date": "2026-02-30"}, {"start_date": None},
    {"trip_type": "multi"}, {"date_mode": "fixed"}, {"date_mode": "fixed", "trip_type": "one_way", "end_date": "x"},
    {"departure_days": []}, {"departure_days": [7]}, {"departure_days": ["3", 3, 1]}, {"return_days": None},
    {"departure_min": "18:60"}, {"departure_min": "24:00"}, {"departure_min": "9:00"}, {"departure_min": "20:00", "departure_max": "19:00"},
    {"return_arrival_max": "22:15"}, {"min_nights": 3}, {"min_nights": 1, "max_nights": 5}, {"max_nights": 31}, {"max_nights": "2"},
    {"max_stops": 4}, {"max_stops": True}, {"adults": 0}, {"adults": 5, "children": 5}, {"infants": 2, "adults": 1}, {"children": "-1"},
    {"budget": "abc"}, {"budget": "nan"}, {"budget": "inf"}, {"budget": 0}, {"budget": True}, {"budget": "250.5"}, {"budget": None}, {"budget": 100001},
    {"return_airport": "x"}, {"airline_mode": "low_cost"}, {"baggage": "none"}, {"seat": "first"}, {"allow_self_transfer": "yes"},
    {"departure_duration_min": 100, "departure_duration_max": 50}, {"min_layover_minutes": 500, "max_layover_minutes": 100},
    {"airlines": "fr, u2 FR"}, {"airlines": ["FRA"]}, {"airlines": 5}, {"final_arrival_max": "25:00"}, {"final_arrival_max": "21:30"},
    {"destinations": "BCN MAD;LIS"}, {"destinations": [f"X{i}" for i in range(3)]}, {"destination_set": "sun", "destinations": []},
    {"destination_set": "moon"}, {"return_departure": "group", "destinations": []}, {"return_departure": "group"},
    {"departure_days": [1], "return_days": [1], "min_nights": 2, "max_nights": 2}, {"min_destination_hours": 721},
    {"trip_type": "one_way", "return_days": "ignored"}, {"date_mode": "fixed", "trip_type": "round_trip", "start_date": d(5), "end_date": d(5)},
]
requests = []
for v in variants:
    spec = {**base, **v}
    try:
        requests.append({"spec": spec, "ok": asdict(FlexibleRequest.parse(spec))})
    except SearchValidationError as exc:
        requests.append({"spec": spec, "error": str(exc)})
words = ["ve", "venezia", "lond", "londra", "mon", "zurigo", "ZRH", "bcn", "new", "san", "aero", "int", "tok", "pa", "xx", "é", "  ro ", "mil", "Kraków", "porto", "can", "de"]
search = {w: airports.search(w, limit=10) for w in words}
countries = {",".join(o): airports.countries(o) for o in (["VCE", "TSF"], ["BLQ"], ["MXP"], ["VCE"])}
details = {c: airports.details(c) for c in ["VCE", "STN", "XXX", "KIV", "BCN", "JFK", "ZZV", "LTN", "HND"]}
print(json.dumps({"today": TODAY.isoformat(), "requests": requests, "search": search, "countries": countries, "details": details}, default=list))
