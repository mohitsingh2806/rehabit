# Rehabit — Exercise & Rehab Routine App (Android) — Plan

> **Status (2026-11-24): M5 built — all planned milestones done** (progression suggestions from ratings/check-ins/adherence, easier/harder/swap on the checklist with equipment hints, body map + My equipment filter). Next: on-phone testing, content review, verified video links, more content groups. This doc holds all context so any future session
> can pick up from here. **Flaws & fixes (§3) are LOCKED** with the user's refinements (§16).
> **All open questions (§15) answered 2026-11-24**; decisions in §16, which wins over any older text.
> **Generic app (2026-11-24):** content and defaults are written for anyone, never tailored to the
> owner's own conditions. Personal details (schedule, equipment) are entered in first-run setup and
> Settings. Next: M0. Agent instructions: [CLAUDE.md](CLAUDE.md).
>
> **Terminology (locked):** a **Routine** is something you pick and do whenever you want (running
> warm-up, cool-down, trail stretch); never scheduled. A **Plan** is for when you're concentrating on
> something (heel pain, frozen shoulder, hiking prep): scheduled, phased, reminded and tracked.
> Both are lists of exercise + dose.
>
> App name: **Rehabit** (Q21), package `com.mohit.rehabit`.

---

## 1. Context

### Who / why
- Personal use (one user, one Android phone). Not commercial, but must feel polished, not mediocre.
- Goal: pick from a growing catalogue of exercise plans (running warm-up/cool-down, hiking prep,
  stretching routines, condition rehab such as plantar heel pain, frozen shoulder, neck, back, knee,
  IT band, ACL…), either **do one now** or **follow one daily** with a checklist, reminders and
  tracking.
- The **exercise** is the base unit, not the plan, so plans can be mixed and matched.
- **Generic, not personalised.** Plans and routines are written for anyone with that problem or
  activity, not for the owner. Nothing in the content assumes a particular user's side, stage,
  schedule or equipment. The owner only chooses **which generic plans get written first** (C1).
  Running several plans at once is a normal case, so dedupe, daily time totals and "Core only" matter
  from day one.

### Sibling app: Nudge (medicine reminder), `../medicine reminder/`
This app should share Nudge's stack, build pipeline and look-and-feel, and reuse its proven pieces.

| Area | What Nudge does | Reuse here |
|---|---|---|
| Stack | Kotlin 2.0, Jetpack Compose + Material 3, Room, DataStore, Glance widget, coroutines; **manual DI** (`AppContainer`), no Hilt | Same, minus Glance (no widget, Q23) |
| Build | **No Android Studio needed.** GitHub Actions builds + unit-tests on every push, signs with a **permanent keystore** (secrets) so updates keep data, publishes each `main` build as a GitHub Release (`/releases/latest/download/<app>.apk`) | Copy workflow, new repo |
| Reminders | `AlarmManager.setExactAndAllowWhileIdle`, `AlarmReceiver` with notification actions (Taken all / Snooze / Skip), `BootReceiver` for boot / package replaced / time & timezone change, "missed" is never silent | Same engine, **gentler policy** (see §8) |
| Settings | "Make reminders reliable" section: notification permission, exact alarms, battery optimisation (OEM killers) | Copy as-is |
| UI pattern | Bottom nav (4 tabs). Today = big **NOW / NEXT hero card** with one big primary button + day timeline below; done items dim; tap status chip to undo | Same pattern for the Today checklist |
| History | Month calendar green / amber / red + per-day detail | Same, plus trends |
| Data entry | **"Copy AI prompt" → paste into Claude → paste result back** (ImportParser) | **Not reused** (Q6b): user not convinced for this app |
| Data safety | Deleted items archived, not deleted, so history stays correct; "Restart course" makes a fresh copy | Same: plans enrolled as snapshots |
| Theme | Teal `#00897B` primary, coral `#FF7043` accent, system dark mode | Same component style, own colour (Q22) |

Things the user showed they care about while building Nudge:
- Practical over clever. Features that don't earn their place get removed ("I just ate" button was removed).
- Nothing should fail silently (the final "Missed" notification was added because silent misses felt wrong).
- Fast bulk entry (AI-prompt import, "Save & add another").
- Asked for vibration-only reminders.
- Fixed-calendar repeat (alternate days counted from the start date, not shifted by misses).

No agent instruction file (CLAUDE.md / AGENTS.md) exists in Nudge. Proposed for this repo: a
`CLAUDE.md` with the persona (senior Android + UX engineer **and** sports physiotherapist), the
conventions above, and the content-authoring rules in §11.

---

## 2. The idea as originally stated
1. A catalogue of plans; the exercise is the building block.
2. **Mode A, "just do it":** choose a combo of exercises and get the list with how-to and reps.
3. **Mode B, daily routine:** select a plan for daily; it becomes a checklist done through the day or
   in one session (suggested, not strict); track whether it was followed; reminders.

---

## 3. Flaws in the original idea, and fixes

**LOCKED 2026-11-24.** The user's refinements (in §16) override this table where they differ.

| # | Flaw | Why it matters (physio / product view) | Fix |
|---|---|---|---|
| 1 | **"Exercise" alone isn't the unit; "exercise + dose" is** | A calf stretch is 10 dynamic swings in a run warm-up, a 30 s static hold in a cool-down, and 3×30 s, 3×/day for plantar heel pain. If reps live on the exercise, mix-and-match breaks. | Two layers: **Exercise** (what and how, written once) and **Prescription** (dose in context: sets, reps, hold, tempo, sides, rest, frequency). Plans hold prescriptions. |
| 2 | **Plans treated as static lists** | Rehab is *progressive*. Doing phase-1 plantar or frozen-shoulder work for 4 months means stagnation or flare-ups. ACL rehab runs 9–12 months with criteria-based phases. | Plans have **Phases** with entry/exit criteria ("first-step pain ≤ 3/10 for a week → phase 2"). Each exercise links to **regressions / progressions**. |
| 3 | **One frequency per plan** | Inside one rehab plan, frequencies differ: plantar-fascia stretch *before the first step out of bed*, heavy heel raises **every other day** (tissue needs ~48 h), neck chin-tucks hourly at the desk, nerve glides 2–3×/day. | **Per-item schedule**: times/day, daily / every N days / weekdays, plus a time **anchor** (§8). |
| 4 | **Warm-ups / cool-downs aren't time-of-day things** | A running warm-up belongs *before a run*, not at 7 am. Putting it in a daily checklist creates false misses on rest days. | **Routines** (warm-up, cool-down, trail stretch…) are picked and done whenever you want, never scheduled. **Plans** are for when you're concentrating on something: scheduled, reminded, tracked. Same engine underneath. |
| 5 | **Stacking plans causes duplication and overload** | Back + hiking + IT band all include hip-flexor stretch, glute bridge and calf stretch. Three 20-min plans make 60 min/day, and adherence collapses within 2 weeks. | A **Combine plans** option merges several plans into one list with duplicates removed (do once, credit all, larger dose wins). The same dedupe applies automatically when several plans are active. Show **total daily minutes**, mark items **core vs optional**, and offer a **"Core only"** button to do just the core items. |
| 6 | **Mixing plans can produce unsafe combos** | e.g. aggressive end-range stretching ("Goggins"-style long holds) during an acute frozen-shoulder phase or an irritable back; open-chain knee extension early after ACL reconstruction. | Per-exercise **cautions / contraindication tags** plus per-plan **avoid lists**; warn when combining. |
| 7 | **"Followed or not" is the wrong metric on its own** | Doing it doesn't mean it's working. In rehab, **symptoms are the outcome.** Without a pain signal, you can't tell improving from flaring. | **10-second check-ins**: a key symptom per condition, e.g. *first-step heel pain 0–10*, morning back stiffness. Per-session feedback: *too easy / right / too hard / hurt*. Use the **pain-monitoring model**: ≤ 5/10 during exercise is acceptable, settled by next morning, not worsening week to week. |
| 8 | **Content is the real product, and text-only how-to is weak** | Movement is hard to learn from text. Poor form makes rehab useless or harmful. Content quality decides whether the app is world-class or mediocre. | Curated, physio-written content: setup, **3 key cues**, common mistakes, **"you should feel it here / NOT here"**, *why it helps*, plus **curated video links** (verified, reputable sources). Media strategy in §11. |
| 9 | **Condition names are too broad** | "Knee pain" covers patellofemoral pain, patellar tendinopathy, ITB syndrome, meniscus, OA, with *different* plans. "Lower back" differs between flexion-intolerant and extension-intolerant. "ACL" covers prevention, non-operative, and post-op (surgeon protocol, graft type). | Condition plans are **specific variants** with a 2–3 question chooser ("Which sounds like you?"). Post-op plans are **"enter your physio's protocol"** templates, not invented ones. |
| 10 | **Common beliefs baked into plan names** | "IT band stretching": the ITB barely lengthens; ITB syndrome responds to **hip-abductor strength + load / cadence management**. Static stretching *before* running slightly reduces performance; it belongs after. | Plans are written to current evidence and do whatever actually manages the problem (the IT band plan doesn't stretch the IT band). *Why* notes explain this inside the app. |
| 11 | **Medicine-style nagging is wrong for exercise** | Re-notifying every 10 min until done gets notifications muted within a week. Exercise is flexible by your own design. | **One reminder per day or one per time slot** (chosen per plan). No follow-ups, no repeats, no nagging. Simple items can be done and ticked *from the notification*. |
| 12 | **Streaks punish rest** | Rehab and strength need rest days. A streak that breaks on a planned rest or sick day demotivates and pushes overtraining. | **No streaks.** Weekly adherence % against *planned* items. Planned rest ≠ missed. "Excused" days (sick / travel / flare-up) are shown but not counted as failure. |
| 13 | **"List of exercises with details" isn't a guided session** | You're on the floor holding a stretch, not reading a list. Left/right sides get forgotten, and holds get counted too fast. | Full-screen **Player**: hold timer ring, side switching, rep / tempo metronome, rest timer, **voice cues** (offline TTS), haptics, screen kept on. Usable hands-free. |
| 14 | **Two modes built as two features** | Duplicate code, and sessions done in Mode A wouldn't count toward Mode B. | **One engine:** everything runs through the Player. Mode B only adds scheduling. Any session done counts toward today's checklist. |
| 15 | **No substitution path** | "Can't get on the floor at work / no band on the trail / this one hurts" leads to skipping. | Tags (target area, movement pattern, position, equipment) → **Swap** to an equivalent, **Regress** to an easier version. Filters for "standing only / no equipment". |
| 16 | **Editing plans can rewrite history** | Changing a plan's reps shouldn't change what you "did" last month. | Logs store what was actually done (exercise id + dose). Enrollments are **snapshots** (like Nudge's archived medicines). |
| 17 | **Adding new plans "as we go" has no workflow** | Without a clear workflow, the catalogue stalls or turns inconsistent. | Content is **versioned JSON in the repo**, authored by Claude (physio persona), reviewed by you, **validated in CI**, and shipped with each build. Personal additions via the manual **Builder** (no AI import, Q6b). |

---

## 4. Product principles ("world class" here means…)
1. **Two taps to start moving.** Open app → hero card → Start. Or start from the notification.
2. **Hands-free while exercising.** Voice and haptics carry the session; you never need to read mid-stretch.
3. **Physio-quality content.** Every exercise says *why*, *how*, *what you should feel*, and *what's wrong*.
4. **Honest tracking.** Adherence against plan, symptom trend, and rest days treated as normal.
5. **Flexible, not strict.** Suggested timing and grouping, never forced. Any order, any time, ticks count.
6. **Calm reminders.** One per day or per time slot, actionable, never repeated, never nagging.
7. **Safe by default.** Red flags, pain rules and cautions are built in, not hidden in a disclaimer.
8. **Offline, private, yours.** No account and no server. Export/backup to a file.

---

## 5. Domain model (concepts)

```
Exercise ──< used by >── PlanItem (= Exercise + Dose + Schedule + Anchor + core/optional)
                              │
Plan ──< Phase ──< PlanItem   │   (simple plans have exactly one phase)
  │                           │
  └─(enroll)→ Enrollment ──< EnrollmentItem (snapshot of PlanItems, user-tweakable)
                     │
          Agenda(date) = due EnrollmentItems → Tasks (deduped across enrollments)
                     │
       CompletionLog (task done/partial/skipped/excused + actual dose + feedback)
       SessionLog    (any Player run: what was done, when, how long)
       CheckIn       (symptom 0–10 for an enrollment's tracked metric, per day)
```

### Exercise (library entry, written once)
- `id` (stable slug, e.g. `calf-stretch-wall-gastroc`), `name`, `aliases`
- `bodyAreas` (foot, ankle, calf, knee, hip, glute, hamstring, quad, adductor, lumbar, thoracic,
  neck, shoulder, elbow, wrist, core, full-body)
- `kind`: stretch-static, stretch-dynamic, mobility, strength, isometric, activation, balance /
  proprioception, plyometric, nerve-glide, soft-tissue (roller / ball), cardio
- `position`: standing, seated, floor-supine, floor-prone, side-lying, quadruped, kneeling;
  `deskFriendly`, `trailFriendly` flags
- `equipment`: none, wall, chair, step, mat, band, loop band, foam roller, ball, towel, strap,
  dumbbell / backpack, bench, door
- `laterality`: BILATERAL | EACH_SIDE | ALTERNATING
- `measure`: REPS | HOLD | REPS_WITH_HOLD | TIME | DISTANCE
- `defaultDose` (fallback when a plan doesn't specify)
- Content: `why`, `setup[]`, `cues[]` (≤ 3, spoken by TTS), `mistakes[]`, `feel` (where you should
  feel it), `stopIf` (where you should NOT feel it, e.g. "tingling down the leg"), `cautions[]`
- Links: `regressions[]`, `progressions[]`, `substitutes[]` (exercise ids)
- Media: `media[]` (bundled image, user photo/video, YouTube link)
- Meta: `source` (BUNDLED | USER), `references[]`, `reviewed` flag

### Dose
`sets`, `reps`, `holdSec`, `durationSec`, `distanceM`, `restSec` (between sets), `sideRestSec`,
`tempo` (e.g. `3-2-3` = up-hold-down), `load` note ("bodyweight", "+ backpack"), `effort` note
("to 7/10 stretch", "RPE 7").

### Schedule (per PlanItem)
- `timesPerDay` (1..n) **or** `microBreak` (every X min in a window, max N)
- `days`: DAILY | EVERY_N_DAYS (fixed calendar, as in Nudge) | WEEKDAYS mask | ON_EVENT (run days)
- `anchors[]`: one per occurrence (§8)

### Plan
- `id`, `name`, `category` (Running, Hiking, Mobility & Flexibility, Rehab: <region>, Desk / Posture,
  Prevention), `goal`, `whoFor`, `notFor` (→ see a professional), `estMinutes`
- `kind`: ON_DEMAND (shown to the user as a **Routine**: do it whenever) | FOCUSED (shown as a
  **Plan**: scheduled over weeks, phased, reminded, tracked). Only Plans can be enrolled.
- `phases[]`: `name`, `durationHint` ("2–6 weeks"), `entryCriteria`, `exitCriteria`, `items[]`
- `trackedMetric` (optional): e.g. "First-step heel pain (0–10)", with `checkInAnchor` (Wake)
- `redFlags[]`, `painRules`, `avoid[]` (exercise tags / ids to avoid while on this plan)
- `variants` + `chooser` questions (for broad conditions such as knee pain)

### Enrollment (Mode B)
Snapshot of a plan's current phase items at start, with: `startDate`, optional `endDate` / weeks,
current `phase`, per-item tweaks (dose, anchor, on/off), `status` ACTIVE | PAUSED | FINISHED.
Editing a bundled plan later does **not** silently change active enrollments. Show
"Plan updated — review changes?".

---

## 6. The two modes, redesigned

### Mode A — Session ("do it now")
- From Library: pick a **Routine** (or any Plan's exercises), or **build a combo** (pick exercises, or
  **Combine plans**: merge several with dedupe preview and total time) → **Start** → Player.
- Can be saved as a custom routine
- Logged as a SessionLog; if the exercises match today's tasks, **they are ticked off**.

### Mode B — Daily plan
- **Follow** a Plan (choose phase/variant, start date, time slots, reminder: once a day or once per slot).
- **Today** shows a checklist grouped by time slot (Wake · Morning · Desk · Evening · Bedtime),
  with a suggested "do it in one go" option ("All remaining · 14 min → Start").
- Each item: tick ✓ (no Player needed), long-press for **Partial / Skip / Skip — pain / Swap**, tap to open
  detail or Start just that one. Group-level "Start group" and "Done all".
- Order and timing are suggestions: doing an evening item in the morning counts.
- Multiple enrollments at once, deduped.

Both modes use the same **Player** and the same logs.

---

## 7. Screens & UX

Bottom nav: **Today · Library · Progress · Settings** (mirrors Nudge's Today · Medicines · History · Settings).

### Today
- **Hero card** (Nudge pattern): "NOW · Morning · 4 exercises · 8 min" → **[Start]** big button,
  secondary "Mark all done". Before any anchor: "NEXT · Desk break 11:00". When finished:
  "All done 🎉" + tomorrow preview.
- **Check-in chip** when due: "First-step heel pain today?" → 0–10 slider inline (one tap).
- Timeline of anchor groups; done groups collapse and dim; progress ring in the top bar (done / planned
  core items).
- Rest day state: "Planned rest day — light walk optional."
- Empty state: "Pick a plan" → Library, plus "Quick start: Running warm-up / Desk reset".

### Library
- Tabs **Plans | Exercises**. Search. Filter chips: body area, goal, equipment, position, ≤ X min,
  desk-friendly, trail-friendly.
- **Body map** (front / back silhouette, tap a region) → plans and exercises for that region. *(M5)*
- Plan detail: goal, who it's for / not for, phases, items with doses, total time, red flags,
  **[Start now]** and **[Follow daily]**.
- Exercise detail: media, why, setup steps, 3 cues, feel / stop-if, mistakes, regress / progress /
  swap links, "Used in" plans, your history for it.

### Builder *(M4)*
- New routine: add from library (multi-select), drag to reorder, set dose per item (defaults prefilled),
  core / optional, schedule & anchor (if daily).
- Combine plans: select N plans → merged list with duplicates highlighted → choose merge rule.

### Player (full screen, the centrepiece)
- Top: progress (exercise 3/9, time left). Centre: media, name, dose ("3 × 30 s · each side").
- **HOLD**: 3-2-1 lead-in → countdown ring → "Switch sides" (5 s transition) → next set → rest ring.
- **REPS**: big **Set done** button and optional tap counter; optional **tempo metronome** (beeps for
  e.g. 3 s up / 2 s hold / 3 s down, used for heavy heel raises).
- **TIME**: timer (e.g. easy jog 5 min).
- Voice (offline TTS): name, side, cue, countdown "3, 2, 1, switch". Haptic pulse at every transition.
  Works with screen on; screen kept awake. Pause / skip / back / +15 s / edit dose on the fly
  ("save to plan?").
- End: summary (time, done / skipped), optional quick feedback per exercise
  (too easy · right · too hard · hurt), pain-during 0–10 for rehab plans. Ticks today's tasks.

### Progress
- Calendar heatmap (Nudge colours: green = all core done, amber = partial, red = none, grey = rest /
  nothing planned, blue = excused).
- Weekly adherence % per enrollment (bars), **symptom trend line** with check-ins (the most
  motivating screen in rehab: "heel pain 7 → 3 in 5 weeks").
- Per-exercise history: dose over time, last feedback. Session log list.

### Settings
- Anchor times (wake, morning, desk window, evening, bedtime), **day boundary** (midnight, Q12), default reminder (once a day at a time / once per
  slot / off), vibration-only, voice on/off + speech rate, lead-in seconds, default rest, units.
- "Make reminders reliable" (copied from Nudge).
- Backup: export / import JSON file. Content version info.

### Widget / app-icon shortcuts
**Not built (Q23).** Everything starts from the app or a notification.

---

## 8. Reminders design

**Time slots** ("anchors" in code) = flexible time windows, each with a user-set time:

| Anchor | Default | Notes |
|---|---|---|
| Wake | phone's next alarm (`AlarmManager.getNextAlarmClock()`) or 07:00 | Plantar fascia stretch *before standing*, check-ins |
| Morning | 08:00 | |
| Desk (micro-breaks) | every 60 min, 10:00–18:00, weekdays, max 5 | Neck / back / hip-flexor resets; "done" from the notification |
| Midday | 13:00 | |
| Evening | 18:30 | Main session for most rehab |
| Bedtime | 22:00 | Gentle mobility |
| Anytime | no reminder | Covered by a once-a-day reminder if the plan uses one |

Routines (warm-up, cool-down…) have no slots and no reminders; they're started when you want.

Policy (**LOCKED: no nagging**). Each plan picks one of:
- **Once a day**: a single notification at a chosen time ("Heel plan · 5 exercises · 12 min"), or
- **Once per time slot**: one notification at each slot that has undone items ("Evening · 4 exercises · 12 min"), or
- **Off**.

Rules:
1. Fires only if there is something undone for that day/slot. Never repeats, no follow-ups, no
   escalation. Desk micro-breaks count as slots (one notification per break).
2. Actions: **Start**, **Done**, **Snooze 30 min** (one manual snooze, user-initiated only).
   For a single simple item (e.g. 10 chin tucks), the notification shows the cue and **Done**, so it can
   be finished without opening the app.
3. At day boundary, undone items become *missed* for adherence; no extra notification.
4. If reminders are not working (permission / battery optimisation), the app says so on Today and in
   Settings rather than failing silently.
5. Channels: Reminders (default importance), Desk breaks (low), Session in progress (low).

Engine: Nudge's `ReminderScheduler` pattern — one alarm per slot (next occurrence that has due
items) or one daily alarm, plus the next micro-break; re-armed on boot, package replace, time and
timezone change, and after every completion / settings change. Sideloaded, so exact alarms are fine.

---

## 9. Tracking & progress logic
- **Task status**: DONE | PARTIAL | SKIPPED | SKIPPED_PAIN | EXCUSED | MISSED (derived at day end).
- **Day colour**: core items only. Optional items never hurt the score.
- **Weekly adherence** = done core occurrences ÷ planned core occurrences (partial = 0.5).
- **Excused days** (sick / travel / flare-up) are excluded from the denominator and shown in blue.
- **Pain rules** (per plan, default pain-monitoring model): during ≤ 5/10 OK; must settle by next
  morning; next-morning symptom not worse than baseline. Breach → banner: "Ease off: drop a level
  or reduce sets for 2–3 days". Repeated breach → "Consider seeing a physio".
- **Progression suggestions** *(M5)*: 3 consecutive "too easy" + stable symptoms → suggest the
  progression exercise or the next phase dose step; "too hard" / "hurt" → suggest regression.
  Always a suggestion, never automatic.

---

## 10. Safety layer
- First-run one-screen note: "Guidance, not diagnosis. If something below applies, see a doctor
  first." Shown once, re-shown on enrolling a rehab plan for the first time.
- **Red flags** per region (shown on plan detail and enroll):
  - Low back: numbness in the saddle area, new bladder/bowel changes, progressive leg weakness,
    fever, unexplained weight loss, history of cancer, severe constant night pain.
  - Neck: dizziness, double vision, trouble speaking or swallowing, drop attacks, symptoms in both arms,
    after trauma.
  - Knee / ankle: can't bear weight, locking, repeated giving way, hot swollen joint.
  - Calf: swelling + warmth + redness (possible DVT).
  - Shoulder: pain with exertion plus chest / jaw / breathlessness (cardiac).
- `stopIf` per exercise is spoken / shown ("Stop if you feel tingling down the leg").
- Post-surgical plans (e.g. ACL reconstruction) ship as **templates to enter your surgeon's or physio's
  protocol**, plus general-education phases, never as a replacement.

---

## 11. Content strategy

### Storage and shipping
- Bundled content: `app/src/main/assets/content/exercises/*.json` and `plans/*.json` (one file per
  plan; exercises grouped by region), plus `content_version`.
- Loaded read-only from assets at runtime and merged in the repository with **user** content from Room.
  A user can "Customise" a bundled exercise: a user copy with the same id overrides it. Bundled updates
  never clobber user edits.
- **CI content validator** (unit test): unique ids, every referenced id exists, required fields,
  sane doses (hold ≤ 300 s, reps ≤ 100…), cues ≤ 3, every plan has red flags if it's rehab. A broken
  content drop fails the build, not the phone.

### Authoring workflow
1. Claude (physio persona) drafts a content drop following a style guide (plain words, second
   person, 3 cues max, "feel it here", why, references).
2. You review on GitHub or in a review build (exercises flagged `reviewed: false` show a small badge).
3. Merge → CI builds → install the update from Releases. Data kept (permanent signing key).
- ~~In-app AI import~~ **dropped (Q6b)**: user not convinced it's useful. New content comes through
  Claude via the repo; personal additions through the manual Builder.

### Media (decide in Q5)
Options, from least to most effort:
1. Text + cues only (strong cues get you surprisingly far, and TTS reads them).
2. **Your own photo / short video per exercise** (record yourself once; best for personal form).
3. YouTube link per exercise (needs internet; links can rot).
4. Bundled images from an open dataset (e.g. the public-domain *free-exercise-db* has photos, but
   mostly gym/strength moves, few rehab stretches).
5. Custom illustrations (consistent line-art; effort-heavy; LLM-drawn SVG figures are usually poor).

**Locked:** curated video links are part of the content (Claude finds them, checks each one opens
and actually shows that exercise, from reputable physio / sports-medicine channels; never invented).
**Locked (Q5/Q5b):** v1 media = text cues (read aloud) + verified video links + **bundled
open-dataset images** wherever a matching image exists (check and record each dataset's licence;
credit it in an About screen) + **own photos** via the system photo picker (no permissions, copied
into app storage, included in backup; M4). Own video clips are out (they'd need an in-app video
player). Custom illustrations: a possible later upgrade.

### Seed catalogue (first drafts; physio notes)
Exact doses are in the content files; listed here are the shape and the reasoning.

**Running warm-up (Routine, ~8–10 min, dynamic only)**: 3–5 min easy jog or brisk walk → leg swings
front/back + side ×10 each → walking lunge with rotation ×6/side → hip openers (gate in/out) → A-skips
→ high knees → butt kicks → ankle pogo hops ×20 → 2–4 strides of 60–80 m. *No long static holds before
running.*

**Running cool-down (Routine, ~8 min)**: 5 min easy jog/walk → static 30–45 s each: calf
(gastroc, straight knee) and soleus (bent knee), hamstring, quad, kneeling hip flexor, figure-4 glute,
adductor. Optional foam roll.

**Runner strength (Plan, 2×/week)**: single-leg calf raise (straight + bent knee), side plank with
hip abduction, single-leg RDL, split squat, glute bridge → single-leg bridge, tibialis raise.

**Hiking prep (Plan, 2–3×/week, 6–8 weeks before a trek)**: step-ups (+ backpack), **eccentric
step-downs** (downhill knees), reverse lunges, single-leg RDL, wall sit, calf raises, lateral band
walks, side plank, single-leg balance (eyes closed progression), loaded carry / weighted stairs.

**Hiker's trail stretch (Routine, standing only, no mat)**: standing quad, calf on a rock / step,
hamstring on a step, split-stance hip flexor, standing figure-4, chest/pec opener (backpack straps),
upper-trap stretch, gentle spinal rotations.

**Long-hold full-body flexibility ("Goggins-inspired")**: long-duration static/hold work for hips
(couch stretch, pigeon, 90/90), hamstrings, calves, adductors, thoracic spine, plus optional
soft-tissue ball. There's no single official published routine, so this is labelled
*inspired by* and needs your reference (Q16). Best as an evening/standalone session, not pre-run.

**Plantar heel pain ("plantar fasciitis"), Plan with phases**:
- Wake: plantar-fascia-specific stretch (cross leg over knee, pull toes back, 10 × 10 s)
  *before the first step*; check-in: first-step pain 0–10.
- Daily: calf stretch gastroc + soleus 3 × 30 s, 2–3×/day; short-foot / towel scrunch.
- Every other day: **high-load heel raise** (single leg, towel rolled under toes, tempo 3 s up – 2 s
  hold – 3 s down): progress ~12 → 10 → 8 reps per set, adding sets and backpack load over weeks.
- Comfort: frozen-bottle roll. Notes: footwear, load management, it takes weeks to months.

**Neck pain / desk neck (Plan)**: chin tucks, deep neck-flexor nods (supine), upper-trap and
levator stretches, scapular retraction / rows with band, thoracic extension over chair, open book;
Desk anchor micro-breaks.

**Low back pain (Plan, variants)**: chooser for *flexion-sensitive* (sitting / bending hurts) vs
*extension-sensitive* (standing / arching hurts) vs *general/chronic*. Core of all: walking, McGill
big 3 (modified curl-up, side plank, bird dog), glute bridge, hip-flexor stretch; plus variant-specific
(prone press-ups vs knee-to-chest / child's pose), cat–cow, pelvic tilts. Strong red-flag screen.

**Mid/upper back (Plan)**: thoracic extension on roller, open book, thread the needle, cat–cow,
prone Y/T, band pull-aparts, wall angels.

**Knee pain (Plan, variants)**:
- *Front-of-knee / patellofemoral*: side-lying hip abduction, clamshell, banded lateral walk, wall sit
  or Spanish squat isometric, step-downs, bridges, TKE with band; load management.
- *Patellar tendon*: isometrics (Spanish squat / wall sit, 5 × 30–45 s) → heavy slow resistance → decline squats.
- *IT band syndrome* (own plan, §3 #10): hip abductor strength (side-lying abduction, side plank with
  abduction, single-leg squat control), foam roll TFL/glute for comfort, cadence +5–10% and load
  management. Not "ITB stretching".

**Posterior tibial tendon dysfunction (Plan, phases)**: tendon-loading progression, the same idea
as other tendinopathies: isometric inversion against a band/wall (pain-relief, early) → slow
concentric/eccentric band inversion → **heel raises with a ball squeezed between the heels**
(tib-post bias), bilateral → single-leg, adding load → short-foot / arch control, single-leg balance,
step-downs with arch control; calf stretching (gastroc + soleus). Education: supportive footwear /
orthotic, running-load management. Tracked metric: inner-ankle pain on a single-leg heel raise or the
first run km (0–10). Red flags: sudden "pop", rapidly flattening arch, can't do any single-leg heel
raise, a lot of swelling → foot & ankle specialist (later stages need medical management).

**Frozen shoulder (Plan, 3 phases)**: freezing/painful phase: gentle range only, pendulums, table
slides, supported ER with stick, within mild discomfort; frozen phase: progressive stretches (towel IR,
cross-body, wall/finger walks, sleeper caution); thawing phase: add band strengthening (ER/IR, rows,
scapular). Typical course: months.

**ACL**: (a) *Prevention* program (FIFA 11+ / PEP style: Nordic hamstring, single-leg balance,
landing mechanics, lateral hops, squat jumps with knee control). (b) *Post-op*: template + education
phases only (quad sets, heel slides, SLR, patellar mobility…) to be filled with **your** protocol.

**Additional groups (Claude's proposal, per Q3 "add other important groups"):**

| Group | Routines (on demand) | Plans (focused) |
|---|---|---|
| Everyday mobility | 5-min morning mobility, bedtime wind-down, post-travel reset (long flight / drive) | Full-body flexibility 6 weeks; hip mobility for desk workers |
| Desk / posture | Desk reset (2–3 min, standing / seated) | Desk neck & back plan with micro-breaks (Desk slot) |
| Strength foundations | Gym/lifting warm-up | Bodyweight strength 3×/week (home, no equipment); core stability |
| Cycling | Pre-ride warm-up, post-ride stretch | Cyclist's hip & back plan (hip flexor, thoracic, glute strength) |
| Field & court sports (football, cricket, badminton, tennis) | Sport warm-up (FIFA 11+ style) | ACL / ankle injury prevention plan; hamstring injury prevention (Nordics) |
| Swimming / overhead | Shoulder warm-up | Rotator cuff & scapular strength |
| Balance | — | Balance & ankle stability (also post-sprain) |
| More rehab | — | Achilles tendinopathy, ankle sprain return, shin splints (MTSS), hamstring strain, hip/groin pain, sciatica-type leg pain (nerve-glide focused, strong red flags), tennis / golfer's elbow, wrist & thumb (desk / phone), patellar tendinopathy, knee osteoarthritis |

---

## 12. Technical architecture

- **Stack**: same as Nudge (Kotlin 2.0.x, Compose BOM, M3, Navigation-Compose, Room + KSP,
  DataStore, coroutines, manual `AppContainer`); **no Glance** (no widget, Q23). Add: `kotlinx-serialization-json` (content
  + backup), Coil (images), Media3 only if video playback is in scope, a small reorderable-list helper
  (or hand-rolled drag).
- minSdk 26, compile/target 35 (match Nudge). Package `com.mohit.<appname>`.
- **Packages**:
  ```
  content/     asset loader, JSON models, validator (shared by app + unit test)
  data/        Room entities/DAOs (user exercises, plans, enrollments, logs, check-ins), settings
  domain/      pure Kotlin, fully unit-tested:
               Schedule.isDue, AgendaBuilder (date → tasks, dedupe), Adherence, PainRules,
               SessionScript (expands doses → step timeline: lead-in, hold L, switch, hold R, rest…),
               Progression (M5)
  player/      PlayerState machine, TTS + haptics + metronome, keep-screen-on
  reminders/   ReminderScheduler, AlarmReceiver (Start/Done/Snooze/Skip), BootReceiver, Notifier
  ui/          today, library, player, progress, builder, settings, theme, common
  ```
- **Room tables**: `user_exercise`, `user_plan` (JSON blob + metadata), `enrollment`,
  `enrollment_item`, `completion_log` (unique per date + enrollmentItem + occurrence),
  `session_log`, `session_entry`, `check_in`. Bundled content is *not* copied into Room.
- **SessionScript** is the key pure function: Player only plays a list of steps. That makes timers,
  sides, rest and voice lines unit-testable with no UI.
- **Backup**: export all user tables + settings to a JSON file via the system file picker; import restores.
- **CI**: copy Nudge's workflow (unit tests incl. content validator → assemble → verify signing cert →
  Release). New repo. Signing key: new keystore or reuse Nudge's (Q2).

---

## 13. Roadmap (each milestone ends with a usable APK)

| Milestone | Scope | Done when |
|---|---|---|
| **M0 Foundations** | Repo, CLAUDE.md, CI + signing + releases, theme, nav skeleton, content schema + validator test, ~10 exercises | APK installs; validator fails CI on a bad id |
| **M1 Session mode** | Library (list, search, filters), plan/exercise detail, **Player** (hold/reps/time/rest, sides, voice, haptics, metronome), session log. Content drop C1 | Can do running warm-up & cool-down hands-free on the floor / track |
| **M2 Daily plans** | **First-run setup** (wake = phone alarm or fixed time, workdays + hours, slot times, desk breaks, equipment; all editable later), "Is this plan for you?" screen (who for / not for + red flags) before following a rehab plan, follow a plan (phase/variant, time slots; Wake slot from phone alarm; "I ran today" run-day logic), Today checklist + hero card, dedupe, per-slot minute totals + "Core only" button, tick/partial/skip/swap, reminders (once a day / once per slot, desk micro-breaks, notification actions), day boundary | Enrolled plantar + neck plans remind correctly; ticks from notification; reboot-safe |
| **M3 Progress** | Calendar heatmap, weekly adherence, check-ins + trend chart, pain-rule banners, session history | 2 weeks of use show a meaningful picture |
| **M4 Builder & media** | Builder (custom exercise, custom routine/plan, combine plans), own photos, backup/export | Can enter a physio-given plan in < 5 min by hand |
| **M5 Smart** | Phases & progression suggestions, swap/regress UX, body map | Suggests progressions sensibly on real logs |

Content drops in parallel (all generic; order set by what the owner needs first, Q4):
- **C1:** frozen shoulder, neck pain, lower back pain (incl. the running-related version), posterior
  tibial tendon dysfunction, patellar tendinopathy, **plus** running warm-up and cool-down routines.
- **C2:** hiking prep + trail stretch, runner strength, desk reset, morning mobility, plantar heel
  pain, IT band, mid back.
- **C3:** long-hold flexibility ("Goggins-inspired"), knee (patellofemoral), ACL prevention + post-op
  template, field/court sport warm-up.
- **C4+:** the rest of the "Additional groups" table.

---

## 14. Risks
| Risk | Mitigation |
|---|---|
| Content accuracy (health) | Physio-style authoring, references field, `reviewed` flag, red flags, conservative doses, "see a professional" paths |
| Scope creep (it's big) | Milestones each ship something usable; M1 alone is already useful |
| Notification fatigue | One per day or per slot, grouped, never repeated |
| OEM battery killers (Xiaomi/Oppo/Vivo/Samsung) | Reuse Nudge's reliability screen; re-arm on every event |
| Media effort | Media optional per exercise; cues + voice carry v1 |
| Data loss on reinstall | Permanent signing key + JSON backup |

---

## 15. Open questions (recommended default in **bold**)
**All answered on 2026-11-24. §16 is the source of truth;** the list below is kept for reference only.
Nothing open. (No per-user condition intake: the app is generic, see §16 "generic".)

**Scope & platform**
1. Android-only native app, same stack and GitHub-CI build/install flow as Nudge? **Yes.**
2. Separate app or a new section inside Nudge? **Separate app** (different mental model and reminder
   style); reuse Nudge's signing key? **New key.**
3. Which plans do you need *right now* (current issues / activities)? This decides content order.
4. Are you dealing with an injury now? Any plan from a physio/surgeon you want to enter?

**Content**
5. Media for v1: **text + cues + voice, plus optional own photo/video and YouTube link per exercise**?
6. Content authored by Claude as bundled JSON, you review, shipped via CI? **Yes.** Plus AI-paste import? **Yes (M4).**
7. Equipment you have at home / carry (band, loop band, foam roller, mat, step, dumbbells, ball, strap)?
8. Broad conditions as variants with a 2–3 question chooser (knee, back)? **Yes.**
16. "David Goggins routine": do you have a specific video/source? Otherwise **"inspired-by" long-hold
    session, flagged for your review.**

**Daily mode & reminders**
9. Your typical day: wake time, work hours (desk job?), usual exercise time, run days?
10. ~~Reminder style~~ **Answered:** once a day or once per time slot, no nagging (§16).
11. Desk micro-break reminders (hourly, work hours, weekdays)? **Yes, opt-in per plan.**
12. Day boundary: **04:00** (late-night sessions count for the same day)?
13. Tracking granularity: **per item** with group "Done all" (honour system, no Player required)?
14. ~~Streaks~~ **Answered:** no streaks (§16). Weekly adherence % with rest/excused days not
    counted against you stays as the tracking view.
15. Multiple plans active at once with dedupe and a daily-time total? **Yes.**

**Tracking & smarts**
17. Symptom check-ins (0–10) and pain rules in v1 (M3)? **Yes.**
18. Progression suggestions (phases, too-easy → progress)? **M5, data model ready from M0.**
19. Voice cues on by default, with a metronome for tempo exercises? **Yes.**
20. Watch / Health Connect / Strava? **No (not planned).**

**Look & name**
21. App name (ideas: *Limber*, *Rehabit*, *Supple*, *Loosen*, *Kinetic*), or yours?
22. Look: Nudge's layout and components with its own colour (e.g. deep indigo + energetic amber),
    or identical teal/coral family? **Own colour, same structure.**
23. Widget + app-icon shortcuts ("Run warm-up")? **Yes (M3/M5).**
24. Backup/export to file? **Yes (M4).**

---

## 16. Decisions log
| Date | # | Decision |
|---|---|---|
| 2026-11-24 | §3 | Flaws & fixes table **locked** with the refinements below. |
| 2026-11-24 | §3.1 | Exercise + dose split: agreed (user: "implicit"). |
| 2026-11-24 | §3.2 | Plans are progressive with phases; **Claude's job is to author plans in this form.** |
| 2026-11-24 | §3.3 | Per-exercise schedule inside a plan: agreed. |
| 2026-11-24 | §3.4 | Warm-ups / cool-downs etc. are **Routines**: select and do whenever you want, never scheduled. A **Plan** is when you're concentrating on something. |
| 2026-11-24 | §3.5 | Overlap handled by a **Combine plans** option (merge with dedupe) + automatic dedupe across active plans. |
| 2026-11-24 | §3.6 | Cautions / contraindication warnings when combining: agreed. |
| 2026-11-24 | §3.7 | Symptom check-ins + pain rules: agreed. |
| 2026-11-24 | §3.8 | Physio-quality content, **plus video links where possible** (verified, never invented). |
| 2026-11-24 | §3.9 | Condition plans are specific and do whatever actually manages the pain (e.g. IT band plan doesn't stretch the ITB). |
| 2026-11-24 | §3.11 | Reminders: **once a day or once per time slot. No nagging**, no follow-ups, no repeats. Evening sweep and persistent mode dropped. |
| 2026-11-24 | §3.12 | **No streaks.** |
| 2026-11-24 | §3.13 | Full guided Player (timers, sides, voice, haptics, metronome): yes, as far as technically possible. |
| 2026-11-24 | meta | Open questions are asked **one at a time**. |
| 2026-11-24 | Q1 | **Android-only**, same stack as Nudge, same GitHub CI → signed APK → Releases flow. |
| 2026-11-24 | Q2 | **Separate app** with its own name, icon and repo; code copied from Nudge, not shared. |
| 2026-11-24 | Q2b | **New permanent signing key** for this app (own GitHub secrets; user backs it up). |
| 2026-11-24 | Q3 | All four areas wanted (running, hiking, leg rehab, spine & shoulder rehab), **plus other common major groups** Claude judges important → table in §11 "Additional groups". Priority order follows Q4. |
| 2026-11-24 | **generic** | **The app is generic, not specific to the owner's conditions** (user correction). Overrides Q4/Q4b/Q7/Q9/Q11/Q16 below wherever they were personal: content is written for anyone; personal answers become **first-run setup + Settings** (M2); no per-user condition intake. |
| 2026-11-24 | Q4 | *(Revised by "generic".)* The owner's priorities only set the **order**: content drop C1 = frozen shoulder, neck, lower back (incl. running-related), PTTD, patellar tendinopathy, plus running warm-up/cool-down. Personal details are deliberately kept out of this public repo. |
| 2026-11-24 | Q4b | *(Revised by "generic".)* Every rehab plan shows **"Is this plan for you?"** (who it's for / not for, red flags) before you follow it, plus the Q8 chooser for broad conditions. Side (L/R) is picked when following a plan, where relevant. |
| 2026-11-24 | Q5 | Media: text + voice + verified video links + **bundled open-dataset images** where they match (licence checked). |
| 2026-11-24 | Q5b | Own media only if simple → **own photos yes** (photo picker, M4); own video clips **no**. |
| 2026-11-24 | Q6 | Bundled content authored by Claude in the repo, CI-validated, "not reviewed" badge until user OKs it. |
| 2026-11-24 | Q7 | *(Revised by "generic".)* Content may use common home equipment (band, loop band, foam roller, mat, dumbbells/weights, plus everyday step/chair/wall/towel); every equipment exercise has a no-equipment substitute. **"My equipment"** is picked in first-run setup and filters swaps. |
| 2026-11-24 | Q8 | Broad condition plans start with a **2–3 question chooser** that picks the version; switchable later. |
| 2026-11-24 | Q16 | Goggins routine: **"inspired by"** long-hold full-body flexibility session from his publicly shown stretches, generic, with cautions / contraindication tags (warns when combined with e.g. a frozen-shoulder or tendon plan), `reviewed: false`. |
| 2026-11-24 | Q9a–c | *(Revised by "generic".)* Workdays + work hours are entered in **first-run setup** (6-day weeks must be supported). Generic defaults: Mon–Fri, 09:00–18:00. The desk slot sits inside work hours. |
| 2026-11-24 | Q9d | **Wake slot follows the phone's next alarm** (`getNextAlarmClock()`), or a fixed time; chosen in first-run setup (fallback default 06:30). |
| 2026-11-24 | Q9e | *(Generic content rule.)* Plan authors assign each item a **suggested slot** by physio logic; users can move items. Default: **Morning** short block = tendon isometrics, activation, gentle range (e.g. chin tucks, shoulder range). **Night** = stretching & mobility + strength on its days. Default slot times editable (night 21:30). |
| 2026-11-24 | Q9g | Run days are **not scheduled**: a day becomes a **run day** when the user taps "I ran today" or does a running warm-up/cool-down routine. Plan items tagged *not on run day* (e.g. heavy tendon loading) move to the next day with a note ("Moved: you ran today"). Generic feature. |
| 2026-11-24 | Q11 | Desk breaks default: **2 a day** (late morning + mid-afternoon), workdays only, silent/vibrate, Done from the notification, no repeats. Options in setup/Settings: off / 2 a day / every 90 min / every 60 min; times editable. |
| 2026-11-24 | Q12 | Day boundary = **midnight** (strict calendar day). Settings default changed from 04:00 to 00:00. |
| 2026-11-24 | Q13 | Ticking: **per exercise + "Done all" per time slot**, on trust (Player not required). Long-press: Partial / Skip / Skip because of pain. |
| 2026-11-24 | Q15 | Multiple plans: dedupe + **minute totals per slot/day + "Core only" button**; optional items never count against you; nothing auto-removed, no hard cap. Moved from M5 to **M2**. |
| 2026-11-24 | Q17 | Check-ins: **one card on Today with a slider per active rehab plan, 3×/week (Mon/Wed/Fri morning, days editable)**, no notification. Pain rules use check-ins + per-session "hurt"/pain-during feedback (no daily next-morning check). M3. |
| 2026-11-24 | Q18 | Progression: phases + regress/progress links in content from M0; **manual phase/version switch from M2**; automatic suggestions (accept-only, never automatic) in **M5**. |
| 2026-11-24 | Q19 | Player sound default: **voice (offline TTS) + metronome beeps + vibration all on**, one-tap mute in the player. |
| 2026-11-24 | Q20 | **No integrations** (no watch, Health Connect, Strava). Fully offline. |
| 2026-11-24 | Q21 | Name: **Rehabit**. `applicationId` / namespace `com.mohit.rehabit`. |
| 2026-11-24 | Q22 | Look: Nudge's layout/components, colours **forest green `#2E7D32` primary + coral `#FF7043` accent**, light + dark. Since primary is green, "done" states use a check icon + tone (not colour alone); calendar keeps green / amber / red / grey / blue. |
| 2026-11-24 | Q23 | **No widget, no app-icon shortcuts.** Glance dropped from the stack. Wake-alarm slot (Q9d) moved from M5 to M2. |
| 2026-11-24 | Q24 | Backup: **manual Export / Restore to a user-chosen file** (system file picker), includes own photos. M4. |
| 2026-11-24 | defaults | Not asked, low-stakes defaults (change anytime): metric units (kg, km), English, minSdk 26 / target 35. |
| 2026-11-24 | M1 | User: exercise base too small → expanded to **84 exercises** across neck, shoulder, mid/low back, hip, thigh, knee, foot/ankle and running drills (19 not yet used by a plan, ready for C2 plans). Equipment enum gained STICK (everyday). |
| 2026-11-24 | M1 | Lower back plan ships without the Q8 chooser for now: flexion- vs extension-biased items are optional with notes ("use if bending forward eases your pain"). The chooser comes with M2. |
| 2026-11-24 | M1 | Session log stored in Room (`sessions`, `session_exercises`: what was actually done + per-exercise feedback + pain during). Progress tab lists recent sessions until M3. |
| 2026-11-24 | M2 | First-run setup ("Your day") is shown when following the **first plan**, not at app launch, so browsing and routines need no setup. Editable in Settings. |
| 2026-11-24 | M2 | Desk breaks: on/off, **2 a day** at two editable times (workdays only). Work hours dropped (the two desk times are enough); 60/90-minute breaks not built. |
| 2026-11-24 | M2 | Changing phase (or taking an updated phase) **ends the current follow and starts a fresh one today** with the same settings, so past ticks keep pointing at the items they were for. |
| 2026-11-24 | M2 | Reminder engine: one exact alarm at a time (next reminder, or a 03:00 refresh so the wake slot follows alarm-clock changes); each reminder fires once; reminders already older than 15 min are dropped (phone off). Snooze only when the user taps it. Notifications for finished slots are removed automatically. |
| 2026-11-24 | M2 | Run day = "I ran today" chip, or any completed session of a RUNNING routine. Items marked not-on-run-day move to the next day (1-day shift), unless already ticked before the run. |
| 2026-11-24 | M2 | Finished sessions tick the checklist: slot sessions tick their tasks (Done / Partly done); other sessions tick the earliest pending task of each exercise done. |
| 2026-11-24 | Q8 | Chooser implemented as plan `chooser` questions + item `showIf`; first used by the lower back plan ("which position eases your pain?"). |
| 2026-11-24 | M3 | Pause = end the current follow today + keep a paused copy; resume restarts it from today. Paused days are covered by no follow, so history needs no pause log. |
| 2026-11-24 | M3 | Adherence counts **core items on finished days** (today is left out until it's over; excused days excluded); partly done = ½. Calendar marks use the fixed status colours **plus a symbol and legend** (✓ ◐ ✕ E), never colour alone. |
| 2026-11-24 | M3 | Pain-rule insights (suggestions only): check-in up ≥2 vs the previous one or 3 rising in a row → ease off; ≥3 "Hurt" ratings / "Skipped: pain" for a plan in 7 days → easier versions; pain during last session ≥6/10 (within 2 days) → ease off; check-in down ≥2 over the last 6 weeks → encouragement. |
| 2026-11-24 | M3 | Check-in days editable in Your day (default Mon/Wed/Fri); a check-in can also be added any time from Progress. |
| 2026-11-24 | C2/C3 | User said "continue as per you": content before M4. Added the user's originally requested plans: **plantar heel pain, IT band syndrome, front-of-knee (patellofemoral), hiking & trekking prep, runner strength, ACL injury prevention (sport)**, and routines **hiker's trail stretch, long-hold flexibility (inspired by David Goggins; not affiliated), desk reset, morning mobility**. 92 exercises, 17 plans/routines. ACL post-op stays a job for the M4 builder (your surgeon's protocol). |
| 2026-11-24 | §3.6 | "Heads-up with your other plans" card on plan and follow screens when a plan contains an exercise a followed plan advises avoiding (or vice versa). |
| 2026-11-24 | M4 | User content (exercises, plans) is stored in Room as the **same JSON as bundled content**, ids prefixed `my-`, merged live into the library; user plans are category CUSTOM with a single phase. Bundled-content validation still runs on bundled files; the builder checks user items with the same dose/schedule rules. |
| 2026-11-24 | M4 | Combine plans = pick 2+ → merged list (same exercise at same time kept once, bigger dose; followed plans use the current phase) → opened in the builder to edit and save as your own plan/routine. |
| 2026-11-24 | M4 | Own photos for **any** exercise via the system photo picker (no permission), stored downscaled (≤1600 px, EXIF-rotated) in app storage. Shown on the exercise page (not in the player, to keep it uncluttered). |
| 2026-11-24 | M4 | Backup = one JSON file (all tables + settings + photos as base64), saved / opened with the system file picker. Restore replaces everything after a confirmation showing what's in the file. |
| 2026-11-24 | M4 | Check-ins / pain-rule insights also apply to your own plans when you give them a "symptom to track". |
| 2026-11-24 | M5 | Suggestions (accept-only, "Not now" hides one for 14 days): 3× "too easy" in a row → harder version (or +reps / +10 s hold / +30 s); 2× "too hard"/"hurt" → easier version (or a set less, etc.); next phase when ≥14 days in the phase, last check-in ≤3/10 and not rising (if tracked), and ≥70% adherence over the last 2 finished weeks — showing the phase's exit criteria for the user to judge. |
| 2026-11-24 | M5 | Changing one item (swap / dose) ends the current follow and starts a copy today with today's ticks carried over, so history keeps the old item. Side effect: "every N days" items restart their cycle from today. |
| 2026-11-24 | M5 | Easier / harder / swap options in the checklist's long-press menu (from the exercise's regressions / progressions / substitutes); alternatives needing equipment you don't own are labelled. Body map (front/back) and a "My equipment" filter in the Library. |
| 2026-11-24 | Yoga | User asked for yoga. Added as content + two player features: **breath pacer** (a `tempo` on a HOLD/TIME dose = breathe in/out seconds; high beep in, low beep out, ring counts breaths, no countdown beeps) and **flow mode** (plan `flow: true`: after the first pose only a 4 s transition naming the next pose). New kinds YOGA / BREATHING, category YOGA. Content: 22 poses & breathing exercises (beginner-friendly, cautions for head-below-heart poses etc.), routines: gentle sun salutation (flow), morning flow, yoga for runners, gentle yoga for the back, bedtime wind-down; plan: yoga foundations (4 weeks, Mon/Wed/Fri). Library gets category chips on Plans/Routines. |
| 2026-11-24 | Q6b | **AI-prompt import dropped**: user not convinced it's useful. Personal additions via manual Builder (M4). |
