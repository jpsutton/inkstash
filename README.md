# inkstash

A Disney Lorcana collection tracker for Android tablets, made for kids to use. Lay a grid of cards on a table, take one photo, and inkstash reads each card's collector number, finds it in a card database and adds it to your collection. Everything stays on the tablet.

- **Scan** up to nine cards at once (twelve is possible but tricky), check the matches, add them.
- **Collection** with filters for ink, set, rarity, type and place, plus name search.
- **Places:** the General collection, pools (boxes and binders) and temp sets. A temp set borrows cards for a deck. Dissolve it and every card goes back where it came from.
- **Backup** your collection to a file, and load it back after a reinstall.
- Card data and images come from [Lorcast](https://lorcast.com). The app ships a snapshot of the card database and can update it from Settings.

## Install

inkstash runs on Android 8.0 or newer, on tablets with Google Play Services. It is one universal APK.

The easiest way to install and update it is inkstash's F-Droid repository, which works in F-Droid, Droid-ify and Neo Store:

1. On the tablet, open [jpsutton.github.io/inkstash/fdroid](https://jpsutton.github.io/inkstash/fdroid/) and tap **Add to F-Droid**.
2. Install **inkstash** from that repository. New releases then show up as regular app updates.

To add the repository by hand, use the address `https://jpsutton.github.io/inkstash/fdroid/repo` and check that its fingerprint is:

```
(published with the first release)
```

You can also download `inkstash-<version>.apk` from the [Releases page](https://github.com/jpsutton/inkstash/releases/latest) and install it directly. Android asks you to allow installs from your browser the first time.

## Develop

Needs JDK 21 and the Android SDK (platform 37). The first command downloads the card snapshot the app imports on first launch (`curl` and `jq` needed). It is gitignored, and the app still builds and runs without it, with an empty card database until you update it in Settings.

```bash
scripts/fetch-cards.sh
```

```bash
./gradlew test lint assembleRelease
```

The release build has no signing config, so the APK is unsigned. CI signs it. Tests are headless: plain JVM tests for the scan pipeline, and Robolectric for the Room database, the repository and backups. There is no emulator in the loop.

### Layout

| Path | What |
| --- | --- |
| `app/src/main/kotlin/.../data` | Room entities, DAOs, `CollectionRepository`, Lorcast sync, backup |
| `app/src/main/kotlin/.../scan` | `OcrEngine` (ML Kit, bundled model), `CollectorLineParser`, `CardLocator`, `CardMatcher` |
| `app/src/main/kotlin/.../ui` | Compose screens: collection, card detail, scan, review, places, temp set, settings |
| `scripts/` | `fetch-cards.sh`, and `ci/` for versioning and the F-Droid repository |
| `packaging/fdroid/` | F-Droid metadata and icon |

The scan pipeline: ML Kit reads every line of text in the photo; `CollectorLineParser` finds the footer (`42/204 · EN · 1`) that gives the collector number and set; `CardLocator` turns each footer into a card outline and gathers the text inside it; `CardMatcher` looks the card up by set and number, falling back to fuzzy name matching.

### Versions

`VERSION_NAME` in `gradle.properties` is the app version. `versionCode` is `major*10000 + minor*100 + patch`. `scripts/ci/version.sh` prints the version (`--code` for the code).

### Releases and secrets

Work goes through `staging`. A pull request from `staging` into `main` (merged with **Create a merge commit**) is a release: `build.yml` builds the APK on that pull request, and `release.yml` signs that same build, tags `v<version>`, creates the GitHub Release and updates the F-Droid repository on the `gh-pages` branch. Bump `VERSION_NAME` before each release, because the workflow refuses an existing tag.

The release workflow uses four repository secrets:

| Secret | What it is |
| --- | --- |
| `INKSTASH_APK_KEYSTORE` | The APK signing key, as a base64 PKCS#12 file |
| `INKSTASH_APK_KEYSTORE_PASS` | Its password |
| `INKSTASH_FDROID_KEYSTORE` | The F-Droid repository key, as a base64 PKCS#12 file (alias `inkstash-fdroid`) |
| `INKSTASH_FDROID_KEYSTORE_PASS` | Its password |

`release.yml` also has a manual dry run (Actions › release › Run workflow) that signs the newest build of a branch and uploads the result as an artifact, without releasing.

#### Release keys (one-time setup, by a person)

1. **APK signing key.** It can never change: Android refuses an update signed by another key. Back it up.

   ```bash
   keytool -genkeypair -keystore inkstash-apk.p12 -storetype PKCS12 -alias inkstash -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=inkstash"
   ```

   ```bash
   base64 -w0 inkstash-apk.p12 | gh secret set INKSTASH_APK_KEYSTORE -R jpsutton/inkstash
   ```

   ```bash
   gh secret set INKSTASH_APK_KEYSTORE_PASS -R jpsutton/inkstash
   ```

2. **F-Droid repository key.** It signs the repository's index, and tablets check its fingerprint. The alias must be `inkstash-fdroid`.

   ```bash
   keytool -genkeypair -keystore inkstash-fdroid.p12 -storetype PKCS12 -alias inkstash-fdroid -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Inkstash F-Droid repository"
   ```

   ```bash
   base64 -w0 inkstash-fdroid.p12 | gh secret set INKSTASH_FDROID_KEYSTORE -R jpsutton/inkstash
   ```

   ```bash
   gh secret set INKSTASH_FDROID_KEYSTORE_PASS -R jpsutton/inkstash
   ```

3. **GitHub Pages.** After the first release creates the `gh-pages` branch: Settings → Pages → Deploy from a branch → `gh-pages`, `/ (root)`. The repository is then at `https://jpsutton.github.io/inkstash/fdroid/repo`. Put its fingerprint (printed at the end of the release's F-Droid step, and shown on that page) in the Install section above.

## Credits and license

inkstash is MIT licensed (see `LICENSE`). The Fredoka and Nunito fonts are under the SIL Open Font License (`FONTS-OFL.txt`).

Card data and images come from [Lorcast](https://lorcast.com). Portions of the materials used are property of Disney/Ravensburger. ©Disney/Ravensburger. Used under Ravensburger's Community Code Policy. We are expressly prohibited from charging you to use or access this content. inkstash is not published, endorsed, or specifically approved by Disney or Ravensburger.
