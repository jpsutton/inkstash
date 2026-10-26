#!/usr/bin/env bash
# Download every Lorcana card from the Lorcast API into app/src/main/assets/cards.json, the snapshot
# the app imports on first launch (so it works offline). The file is gitignored; CI runs this before
# the build.
#
#   scripts/fetch-cards.sh [output-file]
#
# Politeness (https://lorcast.com/docs/api): 50-100 ms between requests. Needs curl and jq.
set -euo pipefail

base=https://api.lorcast.com/v0
out=${1:-"$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets/cards.json"}
mkdir -p "$(dirname "$out")"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

get() { curl -fsS --retry 3 --retry-delay 2 -H 'Accept: application/json' "$1"; }

get "$base/sets" | jq -r '.results[].code' > "$tmp/codes"
[ -s "$tmp/codes" ] || { echo "no sets returned by $base/sets" >&2; exit 1; }

n=0
while read -r code; do
    sleep 0.1
    # Same filter as the in-app sync: skips odd one-off sets with long codes.
    [ "${#code}" -le 4 ] || continue
    get "$base/sets/$code/cards" | jq -c '.[]' >> "$tmp/cards.ndjson"
    n=$((n + 1))
done < "$tmp/codes"

# Keep only the fields the app reads.
jq -s '[.[] | {id, name, version, collector_number, set: {id: .set.id, code: .set.code, name: .set.name},
               ink, inks, inkwell, cost, type, rarity, image_uris: {digital: .image_uris.digital},
               prices: {usd: .prices.usd, usd_foil: .prices.usd_foil}}]' \
    "$tmp/cards.ndjson" > "$out"
echo "$(jq length "$out") cards from $n sets -> $out"
