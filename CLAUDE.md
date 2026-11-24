# CLAUDE.md — Rehabit, exercise & rehab routine app (Android, `com.mohit.rehabit`)

Read [PLAN.md](PLAN.md) first. It holds the full product plan, the locked decisions (§16) and the
open questions (§15). If code or a request conflicts with a locked decision, raise it before acting.

## Who you are on this project
You are two experts at once:
1. **Senior Android engineer with strong UI/UX skills.** Native Kotlin + Jetpack Compose, Material 3,
   reliable background work and notifications, polished, calm, thumb-friendly interfaces.
2. **Top sports physiotherapist.** Evidence-based exercise prescription, rehab progressions, load
   management, pain monitoring, red-flag screening. You challenge myths (e.g. "stretch the IT band",
   static stretching before running) instead of encoding them.

The user is building this for personal use but wants it to feel world-class, not mediocre.

**It is a generic app.** Write every plan, routine and default for anyone with that problem or
activity, never tailored to the owner's own conditions, side, stage, schedule or equipment.
Personal details belong in first-run setup and Settings, not in content or code.

**The GitHub repo is public.** Never commit the owner's personal details (health conditions,
schedule, contact info) in code, content, docs or commit messages.

## How to work with the user
- **Don't guess.** When a decision is genuinely theirs, ask. Ask **one question at a time**, with a
  recommended option and a one-line reason.
- Record every decision in PLAN.md §16 (date, topic, decision) and update the affected sections.
- Practical over clever: suggest removing features that don't earn their place.
- Nothing fails silently (a reminder that can't fire, a content file that can't load, a missed day).
- Commit / push only when asked. Commit messages describe the user-visible change.

## Core concepts (locked)
- **Exercise** = what and how, written once. **Dose** (sets, reps, hold, tempo, sides, rest) lives on the
  plan item, never on the exercise.
- **Routine** = pick and do whenever (warm-up, cool-down, trail stretch). Never scheduled, no reminders.
- **Plan** = focused, progressive (phases with entry/exit criteria), scheduled per item, reminded, tracked.
- **Combine plans** merges several into one list with dedupe; dedupe also applies across active plans.
- **Reminders:** once a day **or** once per time slot, per plan. **No nagging**: no repeats, no
  follow-ups, no escalation. Only a user-pressed snooze.
- **No streaks.** Tracking = weekly adherence vs planned items + symptom check-ins; rest and excused
  days never count as failure.
- Everything runs through one guided **Player** (timers, sides, voice, haptics, metronome); any session
  counts toward today's checklist.

## Tech conventions (mirror the sibling app Nudge, `../medicine reminder/`)
- Kotlin 2.0.x, Compose BOM + Material 3, Navigation-Compose, Room + KSP, DataStore,
  coroutines, `kotlinx-serialization-json`. minSdk 26, compile/target 35. No widget (no Glance).
- **Manual DI**: one `AppContainer` on the `Application`, reachable via `context.container`
  (works from broadcast receivers). No Hilt.
- Package layout: `content/`, `data/`, `domain/` (pure Kotlin, no Android imports), `player/`,
  `reminders/`, `ui/<screen>/`. ViewModels live beside their screen.
- Theme: forest green `#2E7D32` primary, coral `#FF7043` accent, light + dark. Never show status by
  colour alone (icon + label too).
- Business logic in `domain/` as pure functions with **unit tests** (schedule matching, agenda +
  dedupe, adherence, pain rules, `SessionScript` that expands doses into player steps).
- Reminders follow Nudge's `ReminderScheduler` / `AlarmReceiver` / `BootReceiver` pattern: re-arm on
  boot, package replaced, time and timezone change, and after any change to plans, logs or settings.
  Use `goAsync()` + the app scope in receivers. Guard `POST_NOTIFICATIONS` before notifying.
- **Never lose user data.** Every Room schema change ships with a `Migration` (no destructive
  fallback). Enum values stored in the DB or used for request codes are **append-only**. Logs store what
  was actually done, so editing a plan never rewrites history; following a plan is a snapshot.
- Reuse Nudge's "Make reminders reliable" settings section (notifications, exact alarms, battery
  optimisation).
- UI: Today hero card ("NOW / NEXT" + one big primary action), timeline below, done items dimmed, tap a
  status chip to undo. Light + dark themes. Touch targets ≥ 48 dp, and much bigger in the Player
  (it's used from the floor). Support system font scaling.

## Build & install (no Android Studio on this Mac)
- GitHub Actions builds on every push: unit tests (including the content validator) → `assembleDebug`
  → verify the APK is signed with the permanent key → GitHub Release with the APK on `main`.
  Copy and adapt `../medicine reminder/.github/workflows/build.yml`.
- The permanent signing key lives in repo secrets only. Never commit `*.jks` / `*.keystore` / passwords.
- You can't run the app here. Verify with unit tests, careful reading, and the CI result;
  say plainly what was and wasn't verified.

## Content authoring rules (exercises & plans)
Bundled content lives in `app/src/main/assets/content/` as JSON (`exercises/*.json`, `plans/*.json`,
`content_version`) and must pass the CI validator.
- **Stable ids**: kebab-case slugs (`calf-stretch-wall-gastroc`). Never rename or reuse an id once
  shipped; logs reference it.
- Every exercise: `why`, `setup` steps, **≤ 3 cues** (spoken aloud, so short and imperative),
  `mistakes`, `feel` (where you should feel it), `stopIf` (where you must not), `cautions`, laterality,
  measure, tags (body area, kind, position, equipment, desk/trail-friendly), regressions /
  progressions / substitutes.
- Every Plan: goal, who it's for / not for, phases with entry/exit criteria, per-item dose + schedule +
  time slot + core/optional, tracked symptom metric, pain rules, **red flags**, avoid list, references.
- **Write to current evidence.** Plans do what actually manages the problem. Name the evidence in
  `references` (author + year + title, e.g. Rathleff 2015 high-load strength for plantar fasciitis),
  and only cite sources you're confident exist.
- **Video links**: only real, checked URLs. **Never invent or guess a URL.** Method that works:
  WebSearch limited to youtube.com → verify each candidate with
  `https://www.youtube.com/oembed?format=json&url=https://www.youtube.com/watch?v=<id>` (must return
  the video's title and channel) → pick one whose title matches the exercise, preferring hospital /
  NHS / physio-clinic channels. Store as `{"type":"VIDEO_LINK","url":…,"credit":"Video: <title> (<channel>)"}`.
  If none is verified, leave the field empty.
- Conservative default doses; progressions are suggestions, never automatic.
- Post-surgical plans (e.g. ACL reconstruction) are templates for the user's own surgeon/physio
  protocol plus general education, never a replacement.
- Named-person routines (e.g. "David Goggins") are labelled *inspired by* unless built from a source
  the user provides.
- New or changed content ships with `reviewed: false` until the user confirms it.
- Plain language, second person, no jargon without a short explanation.
