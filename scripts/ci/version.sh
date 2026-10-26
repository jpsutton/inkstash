#!/usr/bin/env bash
# Print the app version (VERSION_NAME in gradle.properties), or its versionCode with --code.
# versionCode = major*10000 + minor*100 + patch, the same rule as app/build.gradle.kts.
set -euo pipefail
cd "$(dirname "$0")/../.."
version=$(sed -n 's/^VERSION_NAME=//p' gradle.properties)
[[ $version =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)(-.*)?$ ]] || { echo "bad VERSION_NAME: '$version'" >&2; exit 1; }
if [ "${1:-}" = --code ]; then
    echo $((BASH_REMATCH[1] * 10000 + BASH_REMATCH[2] * 100 + BASH_REMATCH[3]))
else
    echo "$version"
fi
