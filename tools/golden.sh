#!/bin/bash
# Rigenera dal portale Python i casi di riferimento dei test (e, con "dati", i dati).
# Il portale e' il repo flight-finder accanto a questo, oppure FLIGHT_FINDER_PORTAL.
set -euo pipefail
cd "$(dirname "$0")/.."
PORTAL="${FLIGHT_FINDER_PORTAL:-../flight-finder}"
PY="$PORTAL/.venv/bin/python"
[ -x "$PY" ] || { echo "Python del portale non trovato in $PY"; exit 1; }
export FLIGHT_FINDER_PORTAL="$(cd "$PORTAL" && pwd)"
if [ "${1:-}" = "dati" ]; then "$PY" tools/export_data.py; node tools/export_countries.mjs; fi
F=data/fixtures
for name in queries decode jobs browse misc; do
  echo "golden_$name"
  "$PY" "tools/golden_$name.py" > "$F/$name.json"
done
