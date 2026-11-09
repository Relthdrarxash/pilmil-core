#!/usr/bin/env bash
# Régénère les captures en pleine résolution (1080 x 2340) d'une appli via son module Paparazzi
# (classes MarketingScreens*Test) et les range dans <appli>/docs/marketing/captures/<fr|en>/.
# Usage : capture.sh <dépôt de l'appli>
set -euo pipefail

[ $# -eq 1 ] || { echo "usage : capture.sh <depot de l appli>" >&2; exit 2; }
app="$(cd "$1" && pwd)"
snap="$app/screenshots/src/test/snapshots/images"

(cd "$app" && ./gradlew --offline :screenshots:recordPaparazziDebug --tests '*Marketing*')

for lang in fr en; do
  if [ "$lang" = fr ]; then cap=Fr; else cap=En; fi
  mkdir -p "$app/docs/marketing/captures/$lang"
  for f in "$snap"/*_MarketingScreens${cap}Test_*.png; do
    nom="$(basename "$f")"
    nom="${nom#*_MarketingScreens${cap}Test_}"
    cp "$f" "$app/docs/marketing/captures/$lang/${nom#captures_}"
  done
  # Les images brutes de Paparazzi restent hors dépôt (dossier ignoré), on les retire pour ne pas encombrer.
  rm -f "$snap"/*_MarketingScreens${cap}Test_*.png
done
ls "$app/docs/marketing/captures/fr" "$app/docs/marketing/captures/en"
