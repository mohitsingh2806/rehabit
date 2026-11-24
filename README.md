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
Milestone 1: Library (plans, routines, now 114 exercises incl. yoga; search and filters), plan detail with phases
and red flags, and a hands-free guided player (timers, left/right sides, voice cues, beeps and
metronome, vibration) with a session log.
Milestone 2: follow plans daily: set up your day, a Today checklist spread over time slots (merged
across plans, Core only, "I ran today"), and gentle reminders (once per slot or once a day, never
repeated) with Start / Done / Snooze.
Milestone 3: Progress: month calendar (each day marked with colour + symbol, tap for details, mark
sick / travel / flare-up days), weekly progress per plan, quick symptom check-ins with trend charts,
and pain-rule tips ("ease off" / "nice progress").
Milestone 4: build your own exercises, routines and daily plans (e.g. from your physio), combine
plans, add your own photo to any exercise, and export / restore a backup file.
Milestone 5: suggestions to step up or ease off (from your ratings, check-ins and adherence),
easier / harder / swap options right on the checklist, and a body map in the Library.

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
  data/              Room database (sessions, followed plans, ticks, run days, check-ins, your content),
                     settings, Agenda / History / UserContent / Backup repositories
  domain/            pure logic, unit-tested: Agenda (what's due today, merging, reminders), History
                     (day scores, weekly adherence, pain-rule insights),
                     SessionScript (doses → player steps), dose/schedule text
  player/            SessionAudio: offline voice, beeps, vibration
  reminders/         ReminderScheduler (AlarmManager), AlarmReceiver, BootReceiver, Notifier
  ui/                Compose screens: today, library, builder, follow, setup, player, progress, settings
```
