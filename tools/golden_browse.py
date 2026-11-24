"""Insiemi casuali di viaggi: filtri, ordinamenti, gruppi e suggerimenti del Python."""
import os
from pathlib import Path
import sys
# Il portale Python (repo flight-finder): accanto a questo progetto, o dove dice FLIGHT_FINDER_PORTAL.
PORTAL = Path(os.environ.get("FLIGHT_FINDER_PORTAL", Path(__file__).resolve().parents[2] / "flight-finder"))
sys.path.insert(0, str(PORTAL))

import json, random, sys
from pathlib import Path
from flightcore.result_browser import ResultFilters, browse_results

rnd = random.Random(11)
DEST = ["BCN", "STN", "LTN", "LGW", "MAD", "BER", "CDG", "ORY"]
AIR = [("FR", "Ryanair"), ("U2", "easyJet"), ("VY", "Vueling"), ("AZ", "ITA")]

def seg(frm, to, day, h, airline):
    return {"from_code": frm, "to_code": to, "departure": f"{day}T{h:02d}:{rnd.choice([0,15,30]):02d}:00+01:00",
            "arrival": f"{day}T{min(h+2,23):02d}:{rnd.choice([0,10,59]):02d}:00+01:00", "airline_code": airline[0],
            "airline_name": airline[1], "flight_number": str(rnd.randint(1, 999)), "duration_min": rnd.randint(60, 200)}

def trip(i):
    d = rnd.choice(DEST); o = rnd.choice(["VCE", "TSF"])
    day = f"2027-01-{rnd.randint(1, 28):02d}"
    out = {"segments": [seg(o, d, day, rnd.randint(5, 21), rnd.choice(AIR))], "duration_min": rnd.randint(60, 300)}
    rt = rnd.random() < 0.8
    t = {"id": f"t{i:04d}", "price": rnd.choice([rnd.randint(30, 200), round(rnd.uniform(30, 200), 2)]), "outbound": out,
         "inbound": None, "useful_minutes": None, "needs_recheck": rnd.random() < 0.2}
    if rt:
        away = d if rnd.random() < 0.85 else rnd.choice(DEST)
        bday = f"2027-02-{rnd.randint(1, 28):02d}"
        t["inbound"] = {"segments": [seg(away, o, bday, rnd.randint(5, 23), rnd.choice(AIR))], "duration_min": rnd.randint(60, 300)}
        t["useful_minutes"] = rnd.choice([0, rnd.randint(300, 6000)])
        t["destination_airport_change"] = away != d
    return t

cases = []
for n in range(300):
    trips = [trip(i) for i in range(rnd.randint(0, 80))]
    if trips and rnd.random() < 0.3:  # prezzi e ore uguali per i pareggi
        for t in trips[: len(trips) // 2]:
            t["price"] = 50; t["useful_minutes"] = t["useful_minutes"] and 2000
    args = {}
    if rnd.random() < 0.3: args["max_price"] = str(rnd.randint(40, 200))
    if rnd.random() < 0.2: args["min_hours"] = str(rnd.randint(5, 80))
    if rnd.random() < 0.2: args["origin"] = rnd.choice(["VCE", "TSF"])
    if rnd.random() < 0.2: args["destination"] = ",".join(rnd.sample(DEST, 2))
    if rnd.random() < 0.2: args["airline"] = rnd.choice(AIR)[0]
    if rnd.random() < 0.2: args["home_before"] = f"{rnd.randint(8, 23):02d}:{rnd.choice([0, 30]):02d}"
    if rnd.random() < 0.2: args["exclude"] = rnd.choice(DEST)
    sort = rnd.choice(["price", "useful", "duration", "departure"])
    offset = rnd.choice([0, 0, 50, 100])
    focus = rnd.choice(trips)["id"] if trips and rnd.random() < 0.2 else ""
    cases.append({"trips": trips, "args": args, "sort": sort, "offset": offset, "focus": focus,
                  "result": browse_results(trips, sort, offset, ResultFilters.parse(args), focus)})
print(json.dumps(cases, default=list))
