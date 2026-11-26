"""Nomi inglesi delle città per l'app in inglese.

Uso: python3 tools/export_cities_en.py  (cerca il portale in ../flight-finder o in FLIGHT_FINDER_PORTAL)
Le città in italiano del catalogo vengono dagli aeroporti con nome italiano da Wikidata
(flightcore/data/airport_names_it.json nel portale); tutte le altre hanno già il nome di
airportsdata. Qui solo gli esonimi italiani che in inglese cambiano, scritti a mano:
airportsdata a volte dà la località dell'aeroporto (Dakar → Diass) invece della città.
Scrive data/catalog/cities_en.json: codice IATA → città in inglese.
"""
import json
import os
from pathlib import Path

ENGLISH = {
    "Amburgo": "Hamburg", "Anversa": "Antwerp", "Atene": "Athens", "Barcellona": "Barcelona", "Basilea": "Basel",
    "Belgrado": "Belgrade", "Berlino": "Berlin", "Breslavia": "Wrocław", "Bruxelles": "Brussels", "Bucarest": "Bucharest",
    "Calamata": "Kalamata", "Cefalonia": "Kefalonia", "Città del Messico": "Mexico City", "Colonia": "Cologne",
    "Copenaghen": "Copenhagen", "Corfù": "Corfu", "Cracovia": "Kraków", "Danzica": "Gdańsk", "Dublino": "Dublin",
    "Edimburgo": "Edinburgh", "Enfida": "Enfidha", "Erevan": "Yerevan", "Filadelfia": "Philadelphia", "Firenze": "Florence",
    "Francoforte sul Meno": "Frankfurt", "Fès": "Fez", "Gedda": "Jeddah", "Genova": "Genoa", "Ginevra": "Geneva",
    "Il Cairo": "Cairo", "Kuwait": "Kuwait City", "L'Avana": "Havana", "Lemno": "Lemnos", "Lione": "Lyon",
    "Lisbona": "Lisbon", "Londra": "London", "Lublino": "Lublin", "Lussemburgo": "Luxembourg", "Male": "Malé",
    "Marsa Matruh": "Mersa Matruh", "Marsiglia": "Marseille", "Milano": "Milan", "Minorca": "Menorca",
    "Monaco di Baviera": "Munich", "Napoli": "Naples", "Nizza": "Nice", "Pafo": "Paphos", "Palma di Maiorca": "Palma de Mallorca",
    "Parigi": "Paris", "Pechino": "Beijing", "Praga": "Prague", "Riad": "Riyadh", "Rodi": "Rhodes", "Roma": "Rome",
    "Salonicco": "Thessaloniki", "San Paolo": "São Paulo", "Saragozza": "Zaragoza", "Seul": "Seoul", "Siviglia": "Seville",
    "Smirne": "Izmir", "Spalato": "Split", "Stoccarda": "Stuttgart", "Stoccolma": "Stockholm", "Tangeri": "Tangier",
    "Tolosa": "Toulouse", "Torino": "Turin", "Tunisi": "Tunis", "Varsavia": "Warsaw", "Venezia": "Venice",
    "Zagabria": "Zagreb", "Zante": "Zakynthos", "Zara": "Zadar", "Zurigo": "Zurich",
}

APP = Path(__file__).resolve().parents[1]
PORTAL = Path(os.environ.get("FLIGHT_FINDER_PORTAL", APP.parent / "flight-finder"))
CATALOG = APP / "data" / "catalog"
rows = json.loads((CATALOG / "airports.json").read_text())
wikidata = json.loads((PORTAL / "flightcore" / "data" / "airport_names_it.json").read_text())["airports"]
# Città e paese degli aeroporti con il nome italiano: "Colonia" in Germania è Cologne, in Uruguay no.
italian_places = {(rows[code][1], rows[code][3]) for code in wikidata if code in rows}
out = {code: ENGLISH[row[1]] for code, row in rows.items() if row[1] in ENGLISH and (row[1], row[3]) in italian_places}
(CATALOG / "cities_en.json").write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":"), sort_keys=True))
print(len(out), "aeroporti con la città in inglese diversa")
