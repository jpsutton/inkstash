# inkstash project instructions

An Android tablet app (Kotlin, Compose, Room) that tracks a Disney Lorcana collection from photos. See `README.md` for the layout and `PLAN.md` for the V1 plan. `Screens.html` holds the design mockups (a bundled page; the screens are inside its manifest).

## Branches and PRs

- Never work on `main` or `staging` directly. Fetch `origin`, then cut `feat/`, `fix/` or `docs/` branches from `origin/staging` in their own worktree under `.claude/worktrees/`. Unset the upstream so a push can't target `staging`.
- Open PRs into `staging`. A `staging` -> `main` PR is a release: merge it with "Create a merge commit" (`release.yml` reads the PR head from the merge commit's second parent), and bump `VERSION_NAME` in `gradle.properties` first.
- Ask before touching anything about releases or signing: `.github/workflows/release.yml`, `scripts/ci/fdroid-repo.sh`, `packaging/fdroid/`, the keystore secrets.
- Git over HTTPS with the `jpsutton` token, never SSH: `GH_TOKEN=$(gh auth token -u jpsutton)` for `gh`, and the credential-helper form for `git fetch`/`push`.

## Verify headless

Never launch an emulator or install on a device to check your work. Use:

```bash
./gradlew test lint assembleRelease
```

- JVM tests cover `CollectorLineParser`, `CardLocator`, `CardMatcher` and the version-code rule.
- Robolectric tests cover the Room DAOs, `CollectionRepository` (total copies are conserved by move, dissolve and delete), the backup round trip and card import.
- Needs JDK 21 and Android SDK platform 37. `scripts/fetch-cards.sh` writes the gitignored `app/src/main/assets/cards.json`; the build works without it.

## Conventions

- Manual DI: `AppContainer`, no Hilt. ViewModels are built with the `vm { ... }` helper in `ui/Support.kt`.
- Every collection change goes through `CollectionRepository`, one Room transaction per operation.
- A temp-set holding stores its borrowed-from place in `sourceLocationId`. `0` means none (not NULL, so the unique index catches duplicates).
- UI is for a child on a tablet: big touch targets (44 dp or more), plain words, no jargon. Colours and sizes come from the mockups; tokens are in `ui/Theme.kt`.
- The OCR constants in `CardLocator` (`HEIGHT_PER_TEXT` and the footer insets) are estimates. Tune them on photos of real 3x3 and 4x3 grids.
