# Rehabit — exercise & rehab routines (Android)

Pick an exercise **routine** and do it now (running warm-up, cool-down, trail stretch), or follow a
**plan** daily when you're working on something (neck pain, frozen shoulder, tendon rehab…), with a
checklist, gentle reminders and progress tracking. Exercises are the building blocks, so plans can be
combined without repeating the same exercise twice.

See [PLAN.md](PLAN.md) for the full product plan and decisions, and [CLAUDE.md](CLAUDE.md) for
conventions and content-authoring rules.

> Guidance, not diagnosis. Each rehab plan lists warning signs that mean you should see a doctor or
> physiotherapist first.

## Status
Milestone 0 (foundations): app skeleton, theme, navigation, content schema + validator, first
exercises in the Library. Plans, routines and the guided player come next (see PLAN.md §13).

## Install
Every push to `main` builds a signed APK and publishes it as a GitHub Release:
**`https://github.com/mohitsingh2806/rehabit/releases/latest/download/rehabit.apk`**.
Open that link on the phone, then install (allow "Install unknown apps" when asked). Updates install
over the previous version and keep your data, because every build is signed with the same permanent key.

## Build
No Android Studio needed: `.github/workflows/build.yml` runs the unit tests (including the content
validator), builds the debug APK, checks it is signed with the permanent key, and publishes the release.

Locally (optional): Gradle 8.9 + Android SDK 35, then `gradle testDebugUnitTest assembleDebug`.
Without the `REHABIT_KEYSTORE_*` env vars the build is signed with the default debug key and can't
update the installed app.

## Content
Bundled exercises and plans live in `app/src/main/assets/content/` as JSON:
```
content_version          integer, bump on every content change
exercises/<group>.json   array of exercises (what + how, default dose)
plans/<plan-id>.json     one routine (ON_DEMAND) or plan (FOCUSED) per file
```
The schema is `app/src/main/java/com/mohit/rehabit/content/ContentModels.kt`; the rules are in
`ContentValidator.kt` and are enforced by `ContentValidatorTest` in CI.

## Code map
```
app/src/main/java/com/mohit/rehabit/
  RehabitApp.kt      Application + AppContainer (manual DI)
  content/           schema, loader, validator (pure Kotlin) + ContentRepository (assets)
  domain/            pure logic, unit-tested (dose text, …)
  ui/                Compose screens: today, library (list + exercise detail), progress, settings
```
