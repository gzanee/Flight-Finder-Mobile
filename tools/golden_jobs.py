"""Ricerche complete contro un Google finto e deterministico.

Per ogni scenario registra le pagine servite (per tfs), il piano, lo stato
finale del lavoro e alcune viste dei risultati. I test TS rifanno la stessa
ricerca con le stesse pagine e devono ottenere lo stesso risultato.
"""
import os
import tempfile
from pathlib import Path
import sys
# Il portale Python (repo flight-finder): accanto a questo progetto, o dove dice FLIGHT_FINDER_PORTAL.
PORTAL = Path(os.environ.get("FLIGHT_FINDER_PORTAL", Path(__file__).resolve().parents[2] / "flight-finder"))
sys.path.insert(0, str(PORTAL))

import base64, hashlib, json, random, sys
from datetime import date, timedelta
from pathlib import Path
from flightcore import flexible_api
from flightcore.flexible import FlexibleRequest, build_plan
from flightcore.flexible_jobs import JobManager
from flightcore.live_quotes import AirportGroupQuery, LiveQuotes
from flightcore.result_browser import ResultFilters

TODAY = date.today()
AIRLINES = [("FR", "Ryanair"), ("U2", "easyJet"), ("W6", "Wizz Air"), ("AZ", "ITA Airways"), ("LH", "Lufthansa"), ("VY", "Vueling")]

def token(currency="EUR"):
    return base64.b64encode(b"\x1a\x05\x1a\x03" + currency.encode()).decode()

def legs_of(query):
    info = query.pb()
    if isinstance(query, AirportGroupQuery):
        d = info.data[0]
        return [(list(query.origins), list(query.destinations), d.date)], info.trip
    return [([d.from_airport.airport], [d.to_airport.airport], d.date) for d in info.data], info.trip

def wire_leg(frm, to, day, dep, dur, airline, number):
    y, m, d = map(int, day.split("-"))
    arr_total = dep[0] * 60 + dep[1] + dur
    arr_day = date(y, m, d) + timedelta(days=arr_total // 1440)
    arr = divmod(arr_total % 1440, 60)
    fl = [None] * 33
    fl[3:7] = [frm, "x", "y", to]
    fl[8] = [dep[0]] if dep[1] == 0 else list(dep)
    fl[10] = [arr[0]] if arr[1] == 0 else list(arr)
    fl[11] = dur
    fl[20], fl[21] = [y, m, d], [arr_day.year, arr_day.month, arr_day.day]
    fl[22] = [airline, number]
    return fl

def page(query):
    legs, trip = legs_of(query)
    origins, destinations, day = legs[0]
    seed = int(hashlib.sha256(json.dumps([legs, trip]).encode()).hexdigest()[:12], 16)
    rnd = random.Random(seed)
    rows = []
    for o in origins:
        for d in destinations:
            if o == d:
                continue
            for _ in range(rnd.randint(0, 9 if len(origins) * len(destinations) <= 2 else 4)):
                airline, name = rnd.choice(AIRLINES)
                dep = (rnd.randint(5, 23), rnd.choice([0, 5, 30, 45]))
                dur = rnd.randint(55, 200)
                number = str(rnd.randint(100, 9999))
                if rnd.random() < 0.25:  # con scalo a Monaco
                    a = wire_leg(o, "MUC", day, dep, 70, "LH", number)
                    lay = rnd.randint(40, 300)
                    t = dep[0] * 60 + dep[1] + 70 + lay
                    if t >= 1440:
                        continue
                    b = wire_leg("MUC", d, day, divmod(t, 60), dur, "LH", str(int(number) + 1))
                    segs, total = [a, b], 70 + lay + dur
                else:
                    segs, total = [wire_leg(o, d, day, dep, dur, airline, number)], dur
                detail = [None] * 25
                detail[0], detail[1], detail[2], detail[9] = segs[0][22][0], [name], segs, total
                detail[12] = True if (len(segs) > 1 and rnd.random() < 0.3) else None
                price = rnd.choice([rnd.randint(15, 160), round(rnd.uniform(15, 160), 2)])
                if trip == 1:
                    price = round(price * 1.6, 2)
                rows.append([detail, [[None, price], token()], None, None, None, None, None, None, "tok"])
    if rnd.random() < 0.1 and rows:
        rows.append([[None] * 3, [[None, 10], token()]])  # riga non leggibile
    p = [None] * 8
    split = len(rows) // 2
    p[2], p[3] = [rows[:split] or None], [rows[split:] or None]
    if not rows:
        # Risposta vuota nell'involucro osservato
        p = [None] * 24
        p[0] = [None, [[1, 2, 3]], 0, "req", "sess"]
        p[1] = [[[[[c, 0], "o"] for c in origins], [[[c, 0], "d"] for c in destinations]]]
        p[7] = [[], [[], []]]
        p[12] = ["ctx", 0]
        p[23] = 1
    p[7] = p[7] if not rows else [None, [None, [list(a) for a in AIRLINES]]]
    return '<script class="ds:1">AF_initDataCallback({data:' + json.dumps(p) + ",sideChannel:{}});</script>"

def day(n):
    return (TODAY + timedelta(days=n)).isoformat()

def next_weekday(start, wd):
    d = TODAY + timedelta(days=start)
    while d.weekday() != wd:
        d += timedelta(days=1)
    return d

fri = next_weekday(20, 4)
SCENARIOS = {
    "one_way_fixed": {"origins": ["VCE", "TSF"], "destinations": ["BCN", "MAD", "LIS"], "trip_type": "one_way", "date_mode": "fixed",
                      "start_date": day(30), "departure_min": "06:00", "departure_max": "22:00", "budget": 120},
    "weekend": {"origins": ["VCE", "TSF"], "destinations": ["BCN", "STN", "LTN", "BER", "VIE"], "start_date": fri.isoformat(),
                "end_date": (fri + timedelta(days=24)).isoformat(), "departure_days": [4], "return_days": [6],
                "departure_min": "15:00", "return_min": "12:00", "budget": 250, "adults": 2},
    "stops_two_passes": {"origins": ["VCE", "TSF", "VRN"], "destinations": ["BCN", "MAD", "LIS", "OPO", "CDG"], "trip_type": "round_trip",
                         "date_mode": "fixed", "start_date": day(40), "end_date": day(44), "max_stops": 1, "allow_self_transfer": False,
                         "departure_min": "00:00", "return_min": "00:00", "budget": 500, "min_destination_hours": 24},
    "groups": {"origins": ["VCE", "TSF"], "destinations": ["STN", "LTN", "LGW"], "start_date": day(50), "end_date": day(58),
               "departure_days": [3, 4], "return_days": [6, 0], "min_nights": 2, "max_nights": 4, "return_airport": "group",
               "return_departure": "group", "departure_min": "00:00", "return_min": "00:00", "budget": 400,
               "allow_airport_change": True, "max_stops": 1, "allow_self_transfer": True},
    "flex_one_way_low_cost": {"origins": ["VCE"], "destinations": ["BCN", "BER"], "trip_type": "one_way", "date_mode": "flexible",
                              "start_date": day(60), "end_date": day(66), "departure_days": [0, 2, 4, 6], "departure_min": "00:00",
                              "airline_mode": "low_cost", "budget": 90, "final_arrival_max": "21:30", "baggage": "checked"},
    "balanced": {"origins": ["VCE"], "destinations": ["BCN", "MAD"], "date_mode": "fixed", "start_date": day(45), "end_date": day(47),
                 "departure_min": "00:00", "return_min": "00:00", "budget": 1000, "min_destination_hours": 40},
    "nights_range": {"origins": ["VCE", "TSF"], "destinations": ["BER", "VIE", "PRG", "BUD"], "start_date": day(70), "end_date": day(80),
                     "departure_days": [3, 4, 5], "return_days": [0, 6, 1], "min_nights": 1, "max_nights": 3, "departure_min": "00:00",
                     "return_min": "00:00", "budget": 600, "min_destination_hours": 30},
    "anywhere_set": {"origins": ["VCE"], "destinations": [], "destination_set": "sun", "trip_type": "one_way", "date_mode": "fixed",
                     "start_date": day(35), "departure_min": "00:00", "budget": 70},
}

out = {"today": TODAY.isoformat(), "scenarios": {}}
for name, spec in SCENARIOS.items():
    served = {}
    live = LiveQuotes()
    def fetch(query, served=served):
        html = page(query)
        served[query.params()["tfs"]] = html
        return html
    live._fetch = fetch
    manager = JobManager(path=Path(tempfile.gettempdir()) / f"ff-golden-{name}.sqlite3", live=live)
    manager.path.unlink(missing_ok=True)
    request = FlexibleRequest.parse(spec)
    plan = build_plan(request, flexible_api.candidate_routes(request))
    # Senza thread: il lavoro gira qui.
    manager._launch = lambda job_id: None
    job_id = manager.create(request, plan)
    manager.run(job_id)
    job = manager.get(job_id)
    views = {}
    for key, (sort, filters) in {
        "price": ("price", {}),
        "useful": ("useful", {}),
        "duration": ("duration", {}),
        "departure_filtered": ("departure", {"max_price": "150"}),
        "home_before": ("price", {"home_before": "21:00"}),
        "airline": ("price", {"airline": "FR"}),
        "min_hours": ("useful", {"min_hours": "45"}),
        "places": ("price", {"origin": "VCE", "destination": "BCN,BER,STN,LTN", "exclude": "LTN"}),
    }.items():
        v = manager.public(job_id, sort, 0, ResultFilters.parse(filters), "")
        views[key] = v
    out["scenarios"][name] = {"spec": spec, "plan": plan, "served": served, "job": job, "views": views}
print(json.dumps(out))
