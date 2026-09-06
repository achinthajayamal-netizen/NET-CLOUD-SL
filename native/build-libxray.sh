#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
if [[ ! -d native/libXray ]]; then
  git clone --depth 1 https://github.com/XTLS/libXray.git native/libXray
fi
python3 native/libXray/build/main.py android
AAR="$(find native/libXray -type f -name '*.aar' | head -n1)"
if [[ -z "$AAR" ]]; then
  echo "libXray AAR not found" >&2
  exit 2
fi
mkdir -p app/libs
cp "$AAR" app/libs/libXray.aar
echo "Installed app/libs/libXray.aar"
