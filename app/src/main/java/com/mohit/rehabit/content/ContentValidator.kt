package com.mohit.rehabit.content

/**
 * Checks bundled content for mistakes that would confuse or mislead the user. Runs as a unit test
 * in CI (a bad content drop fails the build, not the phone) and in the app (problems are shown in
 * Settings instead of failing silently).
 *
 * Returns human-readable problems; an empty list means the content is valid.
 */
object ContentValidator {

    private val ID = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
    private val TEMPO = Regex("^\\d+(-\\d+){1,3}$")
    const val MAX_CUES = 3
    const val MAX_CUE_LENGTH = 60

    fun validate(bundle: ContentBundle, assetExists: (String) -> Boolean = { true }): List<String> {
        val problems = mutableListOf<String>()
        val known = bundle.exercises.map { it.id }.toSet()

        duplicates(bundle.exercises.map { it.id }).forEach { problems.add("Duplicate exercise id '$it'") }
        duplicates(bundle.plans.map { it.id }).forEach { problems.add("Duplicate plan id '$it'") }

        for (e in bundle.exercises) problems.addAll(checkExercise(e, bundle, known, assetExists))
        for (plan in bundle.plans) problems.addAll(checkPlan(plan, bundle, known))
        return problems
    }

    private fun checkExercise(
        e: Exercise,
        bundle: ContentBundle,
        known: Set<String>,
        assetExists: (String) -> Boolean,
    ): List<String> {
        val p = mutableListOf<String>()
        val at = "Exercise '${e.id}'"
        if (!ID.matches(e.id)) p.add("$at: id must be kebab-case (a-z, 0-9, single dashes)")
        if (e.name.isBlank()) p.add("$at: name is empty")
        if (e.why.isBlank()) p.add("$at: 'why' is empty")
        if (e.feel.isBlank()) p.add("$at: 'feel' is empty")
        if (e.stopIf.isBlank()) p.add("$at: 'stopIf' is empty")
        if (e.bodyAreas.isEmpty()) p.add("$at: needs at least one body area")
        if (e.setup.isEmpty() || e.setup.any { it.isBlank() }) p.add("$at: setup steps missing or blank")
        if (e.cues.isEmpty()) p.add("$at: needs at least one cue")
        if (e.cues.size > MAX_CUES) p.add("$at: ${e.cues.size} cues, max $MAX_CUES (they are spoken aloud)")
        for (cue in e.cues) {
            if (cue.isBlank() || cue.length > MAX_CUE_LENGTH) {
                p.add("$at: cue '$cue' is blank or longer than $MAX_CUE_LENGTH characters")
            }
        }

        p.addAll(checkDose(e.defaultDose, e, "$at default dose"))

        val links = listOf("regressions" to e.regressions, "progressions" to e.progressions, "substitutes" to e.substitutes)
        for ((kind, ids) in links) {
            for (id in ids) {
                if (id == e.id) {
                    p.add("$at: lists itself in $kind")
                } else if (id !in known) {
                    p.add("$at: $kind refers to unknown exercise '$id'")
                }
            }
        }

        if (e.equipment.any { !it.everyday }) {
            val hasPlainSubstitute = e.substitutes.mapNotNull { bundle.exercisesById[it] }
                .any { sub -> sub.equipment.all { it.everyday } }
            if (!hasPlainSubstitute) {
                p.add("$at: uses ${e.equipment.filter { !it.everyday }} but has no no-equipment substitute")
            }
        }

        for (m in e.media) {
            if (m.type == MediaType.VIDEO_LINK && !m.url.startsWith("https://")) {
                p.add("$at: video link must be https: ${m.url}")
            }
            if (m.type == MediaType.IMAGE && !assetExists(m.url)) {
                p.add("$at: image not found in assets/content: ${m.url}")
            }
        }
        return p
    }

    private fun checkPlan(plan: Plan, bundle: ContentBundle, known: Set<String>): List<String> {
        val p = mutableListOf<String>()
        val at = "Plan '${plan.id}'"
        if (!ID.matches(plan.id)) p.add("$at: id must be kebab-case")
        if (plan.name.isBlank()) p.add("$at: name is empty")
        if (plan.goal.isBlank()) p.add("$at: goal is empty")
        if (plan.whoFor.isBlank()) p.add("$at: whoFor is empty")
        if (plan.estMinutes !in 1..180) p.add("$at: estMinutes must be 1-180")
        if (plan.phases.isEmpty()) p.add("$at: needs at least one phase")
        if (plan.kind == PlanKind.ON_DEMAND && plan.phases.size > 1) p.add("$at: a routine has exactly one phase")
        if (plan.category == PlanCategory.REHAB) {
            if (plan.kind != PlanKind.FOCUSED) p.add("$at: rehab content must be a plan (FOCUSED)")
            if (plan.redFlags.isEmpty()) p.add("$at: rehab plans must list red flags")
            if (plan.trackedMetric.isNullOrBlank()) p.add("$at: rehab plans need a tracked metric")
        }
        for (id in plan.avoid) {
            if (id !in known) p.add("$at: avoid list refers to unknown exercise '$id'")
        }
        duplicates(plan.phases.map { it.id }).forEach { p.add("$at: duplicate phase id '$it'") }

        for (phase in plan.phases) {
            val ph = "$at phase '${phase.id}'"
            if (phase.items.isEmpty()) p.add("$ph: has no items")
            phase.items.forEachIndexed { i, item ->
                val where = "$ph item ${i + 1}"
                val exercise = bundle.exercisesById[item.exerciseId]
                if (exercise == null) {
                    p.add("$where: unknown exercise '${item.exerciseId}'")
                } else {
                    if (item.exerciseId in plan.avoid) p.add("$where: '${item.exerciseId}' is also on the avoid list")
                    val dose = item.dose
                    if (dose != null) p.addAll(checkDose(dose, exercise, "$where dose"))
                    val schedule = item.schedule
                    if (plan.kind == PlanKind.ON_DEMAND && schedule != null) {
                        p.add("$where: routines are never scheduled")
                    }
                    if (plan.kind == PlanKind.FOCUSED) {
                        if (schedule == null) {
                            p.add("$where: plan items need a schedule")
                        } else {
                            p.addAll(checkSchedule(schedule, where))
                        }
                    }
                }
            }
        }
        return p
    }

    private fun checkSchedule(s: Schedule, at: String): List<String> {
        val p = mutableListOf<String>()
        if (s.timesPerDay !in 1..12) p.add("$at: timesPerDay must be 1-12")
        if (s.slots.size != s.timesPerDay) {
            p.add("$at: needs one slot per time a day (${s.timesPerDay}), has ${s.slots.size}")
        }
        if (s.days == DayRule.EVERY_N_DAYS && (s.everyNDays ?: 0) < 2) p.add("$at: EVERY_N_DAYS needs everyNDays >= 2")
        if (s.days == DayRule.WEEKDAYS && s.weekdays.isEmpty()) p.add("$at: WEEKDAYS needs at least one weekday")
        return p
    }

    /** Dose must carry the fields its exercise's [Measure] needs, within sane limits. */
    fun checkDose(d: Dose, e: Exercise, at: String): List<String> {
        val p = mutableListOf<String>()
        if (d.sets !in 1..10) p.add("$at: sets must be 1-10")
        fun need(name: String, value: Int?, range: IntRange) {
            if (value == null) {
                p.add("$at: ${e.measure} needs $name")
            } else if (value !in range) {
                p.add("$at: $name must be in $range")
            }
        }
        when (e.measure) {
            Measure.REPS -> need("reps", d.reps, 1..100)
            Measure.HOLD -> need("holdSec", d.holdSec, 1..300)
            Measure.REPS_WITH_HOLD -> {
                need("reps", d.reps, 1..100)
                need("holdSec", d.holdSec, 1..120)
            }
            Measure.TIME -> need("durationSec", d.durationSec, 1..3600)
            Measure.DISTANCE -> need("distanceM", d.distanceM, 1..10_000)
        }
        val rest = d.restSec
        if (rest != null && rest !in 0..600) p.add("$at: restSec must be 0-600")
        val tempo = d.tempo
        if (tempo != null && !TEMPO.matches(tempo)) p.add("$at: tempo '$tempo' must look like 3-2-3")
        if (d.sides != null && e.laterality != Laterality.EACH_SIDE) p.add("$at: 'sides' only applies to EACH_SIDE exercises")
        return p
    }

    private fun duplicates(ids: List<String>): Set<String> =
        ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
}
