"""Pagine Google sintetiche con varianti e difetti, decodificate dal Python.

I test TS devono dare gli stessi preventivi e lo stesso numero di righe scartate.
"""
import os
from pathlib import Path
import sys
# Il portale Python (repo flight-finder): accanto a questo progetto, o dove dice FLIGHT_FINDER_PORTAL.
PORTAL = Path(os.environ.get("FLIGHT_FINDER_PORTAL", Path(__file__).resolve().parents[2] / "flight-finder"))
sys.path.insert(0, str(PORTAL))

import base64, copy, json, random, sys
from pathlib import Path
from flightcore.flexible import FlexibleRequest
from flightcore.live_quotes import LiveUnavailable, LiveTransient, decode

random.seed(7)

def token(currency="EUR"):
    return base64.b64encode(b"\x1a\x05\x1a\x03" + currency.encode()).decode()

def leg(frm="VCE", to="BCN", day=(2026, 12, 24), dep=(18, 5), arr=(20, 0), airline="FR", number="1234", dur=115):
    fl = [None] * 33
    fl[3:7] = [frm, "Partenza", "Arrivo", to]
    fl[8], fl[10], fl[11] = list(dep), list(arr), dur
    fl[17] = "Boeing 737"
    fl[20], fl[21], fl[22] = list(day), list(day), [airline, number]
    return fl

def row(legs, price=30, currency="EUR", names=("Ryanair",), total=None, st=None, tok="synthetic-token"):
    detail = [None] * 25
    detail[0], detail[1], detail[2] = legs[0][22][0], list(names), legs
    detail[9] = total if total is not None else sum(l[11] for l in legs) + 60 * (len(legs) - 1)
    detail[12] = st
    return [detail, [[None, price], token(currency)], None, None, None, None, None, None, tok]

def page(best, other, airlines=None):
    p = [None] * 8
    p[2], p[3] = [best], [other]
    p[7] = [None, [None, airlines or [["FR", "Ryanair"], ["U2", "easyJet"], ["0B", "Blue Air"]]]]
    return '<script class="ds:1" nonce="x">AF_initDataCallback({key: \'ds:1\', hash: \'2\', data:' + json.dumps(p) + ', sideChannel: {}});</script>'

base_rows = [
    row([leg()]),
    row([leg(airline="U2", number="4401", dep=(6, 0), arr=(8, 10))], price=54.99, names=("easyJet",)),
    row([leg(to="MUC", arr=(19, 30), dur=85), leg(frm="MUC", dep=(20, 40), arr=(23, 0), dur=140, airline="LH", number="1812")], price=210, names=("Lufthansa",)),
    row([leg(to="FRA", arr=(19, 30)), leg(frm="HHN", dep=(21, 0), arr=(23, 30))], price=99, st=True),
    row([leg(airline="0B", number="77")], names=("Blue Air",)),
    row([leg(day=(2026, 3, 29), dep=(2, 30), arr=(4, 0))]),  # ora inesistente a Venezia
    row([leg(day=(2026, 10, 25), dep=(2, 30), arr=(3, 30))]),  # ora ambigua
    row([leg(dep=(23, 50), arr=(1, 20))]),
]

mutations = [
    ("identity", lambda r: r),
    ("no_number", lambda r: r[0][2][0][22].__setitem__(1, None)),
    ("empty_price", lambda r: r[1].__setitem__(0, [])),
    ("usd", lambda r: r[1].__setitem__(1, token("USD"))),
    ("no_token", lambda r: r.__setitem__(8, None)),
    ("unknown_airline", lambda r: r[0][2][0][22].__setitem__(0, "Q9Z")),
    ("multi_airline", lambda r: r[0][2][0][22].__setitem__(0, "multi")),
    ("unknown_airport", lambda r: r[0][2][0].__setitem__(3, "QQQ")),
    ("midnight_minutes", lambda r: r[0][2][0].__setitem__(8, [None, 31])),
    ("hour_only", lambda r: r[0][2][0].__setitem__(8, [7])),
    ("no_time", lambda r: r[0][2][0].__setitem__(8, None)),
    ("zero_duration", lambda r: r[0][2][0].__setitem__(11, 0)),
    ("float_duration", lambda r: r[0][2][0].__setitem__(11, 115.0)),
    ("string_duration", lambda r: r[0][2][0].__setitem__(11, "115")),
    ("bad_total", lambda r: r[0].__setitem__(9, None)),
    ("bool_price", lambda r: r[1].__setitem__(0, [None, True])),
    ("string_price", lambda r: r[1].__setitem__(0, [None, "30"])),
    ("negative_price", lambda r: r[1].__setitem__(0, [None, -5])),
    ("bad_date", lambda r: r[0][2][0].__setitem__(20, [2026, 2, 30])),
    ("short_date", lambda r: r[0][2][0].__setitem__(20, [2026, 2])),
    ("two_names", lambda r: r[0].__setitem__(1, ["A", "B"])),
    ("no_names", lambda r: r[0].__setitem__(1, None)),
    ("self_transfer_false", lambda r: r[0].__setitem__(12, False)),
    ("short_detail", lambda r: r.__setitem__(0, r[0][:5])),
    ("no_legs", lambda r: r[0].__setitem__(2, [])),
    ("price_head_number", lambda r: r[1].__setitem__(0, 12)),
]

req = FlexibleRequest.parse({"origins": ["VCE"], "destinations": ["BCN"], "trip_type": "one_way", "date_mode": "fixed",
                             "start_date": "2026-12-24", "end_date": "2026-12-24", "adults": 2, "baggage": "cabin"})
cases = []
def record(name, html, expected=None):
    try:
        quotes, rejected = decode(html, req, "https://example/url", expected_route=expected)
        for q in quotes:
            q.pop("checked_at")
        out = {"quotes": quotes, "rejected": rejected}
    except LiveTransient:
        out = {"error": "LiveTransient"}
    except LiveUnavailable:
        out = {"error": "LiveUnavailable"}
    cases.append({"name": name, "html": html, "expected_route": expected, "result": out})

for i, r in enumerate(base_rows):
    for name, mutate in mutations:
        m = copy.deepcopy(r)
        try:
            mutate(m)
        except IndexError:
            continue
        record(f"row{i}/{name}", page([m, copy.deepcopy(r)], [r]))
# Gruppi e pagine vuote/rotte
record("empty", page(None, None).replace('data:[null, null, [null], [null]', 'data:[null, null, [null], [null]'))
p = [None] * 24
p[0] = [None, [[1, 2, 3]], 0, "req", "sess"]
p[1] = [[[[["VCE", 0], "o"]], [[["ADB", 0], "d"]]]]
p[7] = [[], [[], []]]
p[12] = ["ctx", 0]
p[23] = 1
def shop(payload):
    return '<script class="ds:1">AF_initDataCallback({data:' + json.dumps(payload) + ",sideChannel:{}});</script>"
record("null_groups", shop(p), [["VCE"], ["ADB"]])
record("null_groups_other_route", shop(p), [["VCE"], ["BCN"]])
for slot, value in [(0, None), (0, [None, [], 1]), (1, []), (7, None), (12, []), (12, ["e", 1]), (23, None), (2, {})]:
    q = copy.deepcopy(p); q[slot] = value
    record(f"broken_{slot}_{json.dumps(value)}", shop(q), [["VCE"], ["ADB"]])
record("captcha", "<html>captcha</html>")
record("no_data", '<script class="ds:1">AF_initDataCallback({data:[],sideChannel:{}});</script>')
record("error_response", "<script class=\"ds:1\">AF_initDataCallback({key: 'ds:1',  data:[13,null,[[\"type.googleapis.com/travel.frontend.flights.ErrorResponse\",[]]]],errorHasStatus: true,});</script>")
print(json.dumps(cases))
