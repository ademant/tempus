# AGENTS.md - Tempus (Android Subsonic Client)

## Project Overview
**Tempus** is an open-source Android music client for Subsonic servers. Three product flavors:
- **tempus** (`com.eddyizm.tempus`) - GitHub release with Android Auto & Chromecast
- **degoogled** (`com.eddyizm.degoogled.tempus`) - IzzyOnDroid/F-Droid release, no Google services
- **play** (`com.eddyizm.play.tempus`) - Google Play bundle (AAB), includes Cast

## Workflow

Before implementing any non-trivial change:
1. **Discuss** the approach: explain the plan and tradeoffs, then wait for confirmation
2. **Implement** the change on a branch, not on `main`
3. **Verify** with the flavor-specific unit tests and lint (see Testing)
4. **Commit** only when the user asks; reference the issue with `Closes #NNN` in the commit message or PR description so GitHub closes it on merge

Skip the discussion step only for typos or single-line fixes.

PR rules (target branch, screenshots, `USAGE.md` updates) are in `CONTRIBUTING.md`.

## Build & Development

### Toolchain
- **Gradle**: 9.8.0 (wrapper)
- **AGP**: 9.2.1
- **Kotlin**: 2.2.20
- **Java**: 21 (via Foojay toolchain resolver in `settings.gradle`)
- **compileSdk/targetSdk**: 37
- **minSdk**: 24

### Key Commands
```bash
# Build release APKs (as in CI) and the Play bundle
./gradlew assembleTempusRelease
./gradlew assembleDegoogledRelease
./gradlew bundlePlayRelease

# Build debug APKs (for testing)
./gradlew assembleTempusDebug
./gradlew assembleDegoogledDebug
./gradlew assemblePlayDebug

# Run unit tests (per flavor)
./gradlew testTempusDebugUnitTest
./gradlew testDegoogledDebugUnitTest
./gradlew testPlayDebugUnitTest

# Run lint (per flavor/variant)
./gradlew lintTempusDebug
./gradlew lintTempusRelease
./gradlew lintDegoogledDebug
./gradlew lintDegoogledRelease
./gradlew lintPlayDebug
./gradlew lintPlayRelease

# Run all lint
./gradlew lint

# Run instrumented tests (requires device/emulator)
./gradlew connectedTempusDebugAndroidTest
./gradlew connectedDegoogledDebugAndroidTest

# Clean
./gradlew clean
```

### Build Notes
- Uses **version catalog** (`gradle/libs.versions.toml`) for dependency management
- **ProGuard/R8** enabled for release builds (`minifyEnabled`, `shrinkResources`)
- **Foojay resolver** downloads JDK 21 automatically (no system Java needed)
- Generates **universal APKs** (all ABIs in one APK) via `splits.abi`
- Room schema export to `app/schemas/`
- Mapping files generated for release builds (used for crash deobfuscation)

## Architecture
- **Language**: Kotlin (primary), some Java
- **Architecture**: MVVM-ish with Media3/ExoPlayer for playback
- **Key libs**: Room (DB), Retrofit/OkHttp (network), Media3/ExoPlayer (media), Glide (images), Material3
- **Flavors**: `tempus` and `play` (Media3 Cast) vs `degoogled` (no Cast)
- **Main package**: `com.eddyizm.tempus` (namespace), app IDs differ by flavor

## Testing
- **Unit tests**: JUnit 4 + Mockito + MockK (run via `test*UnitTest` tasks)
- **Instrumented tests**: AndroidX Test + Espresso (run via `connected*AndroidTest`)
- **No CI test execution** currently - tests run locally only
- Test configs in `app/build.gradle`: `testOptions.unitTests.returnDefaultValues = true`

## CI / Release Process
- **Prerelease** (`.github/workflows/github_prerelease.yml`): Triggered on `v*-dev*` tags, builds debug APKs, signs with release keystore
- **Release** (`.github/workflows/github_release.yml`): Triggered on `v*` tags (non-dev), builds the tempus and degoogled APKs plus the play AAB, signs them, creates GitHub release with the artifacts + ProGuard mapping files (gzipped)
- **Signing**: Uses `r0adkll/sign-android-release` action with keystore from secrets
- **Java**: Zulu JDK 21 in CI

## Key Files
- `app/build.gradle` - Main build config, flavors, dependencies
- `gradle/libs.versions.toml` - Version catalog (all versions here)
- `settings.gradle` - Plugin management, Foojay resolver
- `app/proguard-rules.pro` - ProGuard rules (Retrofit, exceptions, line numbers)
- `bin/build.sh` - FFmpeg AAR build script (for custom decoder)

## Contributing Notes (from CONTRIBUTING.md)
- PRs against `development` branch
- Include before/after screenshots for UI changes
- Tests exist, but CI does not execute them; run the flavor-specific unit-test tasks locally.
- Update docs (`USAGE.md`) with changes
- Crash logs are obfuscated in release; deobfuscate with mapping.txt:
  ```bash
  $ANDROID_HOME/cmdline-tools/latest/bin/retrace app/build/outputs/mapping/tempusRelease/mapping.txt stack_error_transcript.txt
  ```

## Common Tasks

| Task | Command |
|------|---------|
| Build debug APK (tempus) | `./gradlew assembleTempusDebug` |
| Build release APK (tempus, degoogled) | `./gradlew assembleTempusRelease assembleDegoogledRelease` |
| Build Play bundle | `./gradlew bundlePlayRelease` |
| Run unit tests (tempus) | `./gradlew testTempusDebugUnitTest` |
| Run lint (all) | `./gradlew lint` |
| Clean build | `./gradlew clean` |
| Generate lint baseline | `./gradlew updateLintBaseline` |

## Gotchas
- **Three app IDs**: `com.eddyizm.tempus`, `com.eddyizm.degoogled.tempus`, `com.eddyizm.play.tempus` - debug adds `.debug` suffix
- **Media3 Cast** only in `tempus` and `play` flavors (`tempusImplementation libs.media3.cast`, `playImplementation libs.media3.cast`)
- **FFmpeg decoder** is a local AAR (`libs/lib-decoder-ffmpeg-release.aar`), built separately via `bin/build.sh`
- **Room schemas** committed to `app/schemas/` - update on schema changes
- **No tests in CI** - run locally before PR
- **Java 21 required** (managed by Foojay in Gradle)

## Proguard mapping for Release

To deobfuscate crash logs:
```bash
$ANDROID_HOME/cmdline-tools/tools/bin/retrace app/build/outputs/mapping/tempusRelease/mapping.txt stack_error_transcript.txt
```

### Development Guidelines

1. **Testing**: Manual testing on physical devices is required - avoid VM testing for network/foreground service features
2. **Database Integrity**: `isDownloaded()` must stay authoritative (Media3 index + Room DB row); external deletes must purge the Media3 index via `DownloaderManager.remove(...)`.
3. **Notification UX**: Single notification with correct actions, tap opens app to queue fragment
4. **Cross-Flavour**: Test on all three flavors (`tempus`, `degoogled`, `play`) as they have different capabilities

## Dancebrainz integration (fork-only)

The Dancebrainz feature (tagging recordings with dances, regions and meters through the dancebrainz JSON API, see `~/src/dancebrainz/API.md`) lives only in this fork and must stay cherry-pickable onto other forks and onto upstream changes.

Rules:
- **All new code goes in `com.eddyizm.tempus.dancebrainz`** (`app/src/main/java/com/eddyizm/tempus/dancebrainz/`). Its own Retrofit client, models, repository and UI. Do not reuse or edit `subsonic/RetrofitClient`.
- **Resources:** new strings in `res/values/strings_dancebrainz.xml`; new layouts and drawables prefixed `db_`. Do not add to `strings.xml` or other shared resource files.
- **Storage:** no Room entities in the shared `AppDatabase`. Keep Dancebrainz data in a separate database file or fetch it from the API, so upstream schema migrations never conflict with it. Preferences use keys prefixed `db_` in their own preferences file.
- **Shared-code hooks are one line each.** Every edit outside the package is a single call into the package, placed in a commit of its own whose message starts with `dancebrainz-hook:`. Current seams:
  1. Navigation: one destination in `res/navigation/nav_graph.xml`, plus one menu item.
  2. Settings: one entry in `SettingsContainerFragment`.
  3. Now playing: one call from the player to read the current track's MBID.
  4. Model: `subsonic/models/Child.kt` must parse `musicBrainzId` (not present today; needed to link playing songs to recordings).
- **Commit discipline:** feature commits touch only the package and its resources. Hook commits touch only the seam files. Cherry-pick feature commits as a range and hooks separately, so conflicts stay in the hook lines.
- Do not change behaviour of existing features to suit the integration. Add a seam instead.
- Keep the feature on for all three flavors (no Google services involved).

## Always
- Run the unit tests and lint for every flavor a change touches, and for all three when touching shared code.
- Use the `./gradlew` wrapper; don't use a system Gradle.
- Commit updated `app/schemas/` files with any Room schema change.
- Update `USAGE.md` for user-visible behaviour changes.
- Test network, playback, and foreground-service changes on a physical device.

## Never
- Commit, push, or tag a release unless the user asks for it.
- Add Cast or Google Play services code to the `degoogled` flavor.
- Change `isDownloaded()` without keeping the Media3 index and Room row in sync.
- Hand-edit `app/lint-baseline.xml`; regenerate it with `./gradlew updateLintBaseline`.
- Build or sign release artifacts unless the user asks for them.

Skills provide specialized instructions and workflows for specific tasks.
Use the skill tool to load a skill when a task matches its description.
<available_skills>
  <skill>
    <name>customize-opencode</name>
    <description>Use ONLY when the user is editing or creating opencode's own configuration: opencode.json, opencode.jsonc, files under .opencode/, or files under ~/.config/opencode/. Also use when creating or fixing opencode agents, subagents, skills, plugins, MCP servers, or permission rules. Do not use for the user's own application code, or for any project that is not configuring opencode itself.</description>
    <location>&lt;built-in&gt;</location>
  </skill>
</available_skills>

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
