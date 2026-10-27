# inkstash — V1 plan

## Context
Android tablet app (with Google Play Services) for user's child to track a Disney Lorcana collection.
V1: photograph a grid of cards, match them against a known-card DB, store copies in a local collection
split across a general pool, specialized pools, and temporary sets (e.g. competition decks).
Plus CI/CD that builds, signs and publishes APKs to an in-repo F-Droid repo on GitHub Pages,
copying the patchbay/freecell setup. Directory `/home/jpsutton/Projects/Personal/inkstash` is empty, not a git repo.

Decisions (from user): GMS tablet, ML Kit bundled OCR, temp sets *move* copies and return them on dissolve,
public repo `jpsutton/inkstash`.

## Stack
- Kotlin, Jetpack Compose + Material 3, Navigation Compose, `gradle/libs.versions.toml` catalog.
  Versions as patchbay (AGP 9.3.x, Gradle 9.6.x, Kotlin 2.2.x) — check for newer at start.
- Room (KSP), kotlinx.serialization, OkHttp, Coil 3 (AVIF decode: native on API 31+, add coil AVIF decoder or fall back for older), CameraX, ML Kit `com.google.mlkit:text-recognition` (bundled model, no GMS download wait).
- Manual DI (`AppContainer`), no Hilt.
- `applicationId`/namespace `io.github.jpsutton.inkstash` (freecell convention). minSdk 26, target/compileSdk latest (36/37). Universal APK (cheap tablets may be 32-bit).
- License MIT (freecell precedent).

## Card data
- Source: Lorcast API `https://api.lorcast.com/v0` — `GET /sets` (`results`), `GET /sets/:code/cards` (plain array). 50–100 ms between requests, cache ≥24 h. Fields used: `id, name, version, collector_number, set{id,code,name}, ink, inkwell, cost, type, rarity, image_uris.digital{small,normal,large}`.
- `scripts/fetch-cards.sh` (curl + jq) writes `app/src/main/assets/cards.json` (gitignored; CI runs it before build) so first launch works offline.
- In-app "Update card database" (Settings) re-syncs from API, upserts by Lorcast `id`. Needs `INTERNET` permission. Images never bundled; Coil loads/caches from Lorcast CDN.
- Footer note: Lorcast/Ravensburger Community Code attribution in About screen.

## Data model (Room)
- `Card` — lorcast id PK, setCode, collectorNumber, name, version, ink, inkable, cost, type, rarity, image URLs. Unique index (setCode, collectorNumber); FTS4 table on name+version for search.
- `Location` — id, name, kind `GENERAL | POOL | TEMP_SET`, notes, createdAt. One `GENERAL` row seeded, not deletable.
- `Holding` — cardId, locationId, foil, quantity, sourceLocationId (non-null only in TEMP_SET rows). Unique (cardId, locationId, foil, sourceLocationId).
- `CollectionRepository` ops, each one Room transaction: `add(card, foil, qty, location)`, `move(card, foil, qty, from, to)` (moving into a TEMP_SET records source), `remove`, `dissolveTempSet(id)` (returns each holding to its sourceLocationId, falls back to GENERAL if source deleted), `deleteLocation` (pool must be empty or contents go to GENERAL).
- Export/import full collection as JSON via SAF (Settings) — protects the kid's data across reinstalls.

## Scan pipeline
1. `ScanScreen`: CameraX preview + `ImageCapture` at max resolution, guide overlay ("lay cards in a grid, fill the frame"). Also "Pick photo" via Photo Picker.
2. ML Kit `TextRecognizer` on full image (`scan/OcrEngine.kt`).
3. `scan/CollectorLineParser.kt`: regex over text lines for Lorcana footer `^(\d{1,3}[a-z]?)\s*/\s*(\w{1,4})\s*[·•.]\s*([A-Z]{2})\s*[·•.]\s*(\w{1,4})` → collectorNumber, set code. Tolerant of OCR swaps (O/0, I/1, l/1, `•` read as `.` or `-`).
4. `scan/CardLocator.kt`: each footer box anchors one card; estimate card rect from footer position (bottom-left) and line height; collect other text lines inside the rect as name candidates.
5. `scan/CardMatcher.kt`: exact (set, number) lookup; if missing/ambiguous, fuzzy-match name candidates (normalized Levenshtein vs name/"name - version") restricted to set if known. Returns match + confidence + top alternates.
6. `ReviewScreen`: one row per detected card — crop thumbnail from photo, matched card image, confidence badge; tap to pick alternate or search; foil toggle; qty; remove row; "+ add missed card" (search). Destination location picker (default GENERAL). Confirm → `CollectionRepository.add`.

## Screens (big touch targets, kid-friendly)
- Collection: grid of owned cards, totals; filters ink / set / rarity / type / location; name search.
- Card detail: art, stats, copies per location+foil, move/remove buttons.
- Locations: list of pools and temp sets with counts; create/rename/delete; temp set detail shows card count, ink mix, cost curve; "Dissolve" action with confirm.
- Scan → Review.
- Settings/About: card DB update + last sync time, export/import, attribution, version.

## Repo, CI/CD, F-Droid (mirror freecell)
- Layout: `app/`, `scripts/ci/{version.sh,fdroid-repo.sh}`, `scripts/fetch-cards.sh`, `packaging/fdroid/io.github.jpsutton.inkstash.yml` + 512/128 px icon, `.github/workflows/{build.yml,release.yml}`, `README.md`, `CLAUDE.md`.
- Version: `VERSION_NAME` in `gradle.properties`; `versionCode = major*10000 + minor*100 + patch` computed in `app/build.gradle.kts`. `scripts/ci/version.sh` prints it. Start at 0.1.0.
- `build.yml`: `pull_request` to `main` and `staging` + `workflow_dispatch`. Java 21. PRs into `staging`: `./gradlew test lint` only. PRs into `main` (and dispatch): fetch-cards, `./gradlew test lint assembleRelease` (no signingConfig → unsigned), upload artifact `inkstash-android`, so a release is built once.
- `release.yml`: `push: branches: [main]`, permissions `contents: write, actions: read`, concurrency `release`. Copy freecell/patchbay steps: find PR build via `HEAD^2` + `gh run list --workflow build.yml`, version, fail if tag exists, zipalign + apksigner with `INKSTASH_APK_KEYSTORE(_PASS)` (warn + skip if unset), tag `v<version>`, `gh release create` with `inkstash-<version>.apk`, then `pipx install fdroidserver==2.4.5` + `scripts/ci/fdroid-repo.sh` with `INKSTASH_FDROID_KEYSTORE(_PASS)`.
- `scripts/ci/fdroid-repo.sh`: copy of `/home/jpsutton/Projects/Personal/patchbay/scripts/ci/fdroid-repo.sh` with names swapped (alias `inkstash-fdroid`, dname `CN=Inkstash F-Droid repository`, repo URL `https://jpsutton.github.io/inkstash/fdroid/repo`, metadata path).
- README: user install section modelled on patchbay `README.md:113-130` (numbered "open page, tap Add to F-Droid", address, fingerprint placeholder until first release); dev section with secrets table (freecell README) and one-time key setup steps (patchbay `remote/README.md:63-95`: keytool commands, `gh secret set ... -R jpsutton/inkstash`, enable Pages on `gh-pages`).
- CLAUDE.md: branches `feat/`/`fix/`/`docs/` from `origin/staging` in `.claude/worktrees/`, PR into `staging`; staging → main PR = release; ask before touching release/signing; tests headless.
- Git: `gh repo create jpsutton/inkstash --public` (HTTPS, `GH_TOKEN=$(gh auth token -u jpsutton)`), initial skeleton commit on `main`, create `staging`, V1 work on `feat/v1` → PR into `staging`.

## Build order
1. Gradle skeleton, theme, nav shell, CI build.yml, README/CLAUDE.md, repo create.
2. Card DB: fetch script, Room entities, asset import, Lorcast sync.
3. Collection model + repository + Locations/Collection/Card detail screens.
4. Scan: camera, OCR, parser, locator, matcher, review screen.
5. Export/import, settings/about.
6. release.yml, fdroid-repo.sh, F-Droid metadata, icon.

## Verification
- JVM unit tests: `CollectorLineParser` (clean + OCR-noisy fixtures, promo/enchanted numbers like `205/204`), `CardMatcher` (exact, fuzzy, ambiguous), version code math.
- Robolectric tests (headless): Room DAOs, `CollectionRepository` add/move/dissolve/delete invariants (total copies conserved), JSON export→import round trip, asset import.
- `./gradlew test lint assembleRelease` locally and green `build.yml` on the PR.
- Release dry check: run `fdroid-repo.sh` against a throwaway keystore + local dir to confirm index generation (no push).
- Human time: generate APK + F-Droid keystores and set 4 secrets; enable Pages after first release; photograph real 3x3 / 4x3 grids on the tablet to tune footer regex and card-rect estimate; install from F-Droid repo on tablet.

## Risks
- Footer text is ~2 mm tall; a 12-card grid may be too small for OCR on some cameras. Mitigation: max-res capture, guide recommending ≤9 cards, review screen + manual add.
- Lorcast v0 is beta; parser for API is lenient (`ignoreUnknownKeys`), bundled snapshot keeps app usable if API breaks.
