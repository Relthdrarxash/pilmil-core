#!/usr/bin/env bash
# Génère tous les visuels (fr et en) d'une appli : build_all.sh <dépôt de l appli>
# Les captures pleine résolution doivent déjà exister (voir capture.sh).
set -euo pipefail
[ $# -eq 1 ] || { echo "usage : build_all.sh <depot de l appli>" >&2; exit 2; }
ici="$(cd "$(dirname "$0")" && pwd)"
python3 "$ici/generate.py" --app-dir "$1"
