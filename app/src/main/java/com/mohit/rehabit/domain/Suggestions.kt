package com.mohit.rehabit.domain

import com.mohit.rehabit.content.Dose
import com.mohit.rehabit.content.Exercise
import com.mohit.rehabit.content.Measure
import com.mohit.rehabit.content.Sides
import com.mohit.rehabit.data.Feedback

/** A rating given at the end of a session. */
data class FeedbackPoint(val exerciseId: String, val feedback: Feedback, val day: Long)

sealed interface SuggestionAction {
    data class Swap(val enrollmentId: Long, val itemIndex: Int, val exerciseId: String) : SuggestionAction
    data class ChangeDose(val enrollmentId: Long, val itemIndex: Int, val dose: Dose) : SuggestionAction
    data class NextPhase(val enrollmentId: Long, val phaseId: String) : SuggestionAction
}

data class Suggestion(
    /** Stable id, used to dismiss it ("not now") for a while. */
    val key: String,
    val harder: Boolean?,
    val title: String,
    val text: String,
    val action: SuggestionAction,
)

/**
 * Progression suggestions from the user's own ratings and symptoms. Always a suggestion the user
 * accepts, never automatic:
 *  - 3 "too easy" in a row → the harder version (or a little more dose)
 *  - 2 "too hard" / "hurt" in a row → the easier version (or a little less)
 *  - a phase that looks done (2+ weeks in, symptoms low and not rising, adherence ≥ 70%) → next phase
 */
object Suggestions {
    const val EASY_STREAK = 3
    const val HARD_STREAK = 2
    const val MIN_DAYS_IN_PHASE = 14
    const val PHASE_SYMPTOM_MAX = 3
    const val PHASE_ADHERENCE_MIN = 70

    fun build(
        plans: List<FollowedPlan>,
        exercises: Map<String, Exercise>,
        feedback: List<FeedbackPoint>,
        checkInsByPlan: Map<String, List<CheckInPoint>>,
        /** Average adherence % over the last two finished weeks, per plan id (null = no data). */
        adherenceByPlan: Map<String, Int?>,
        dismissed: Set<String>,
        today: Long,
    ): List<Suggestion> {
        val out = mutableListOf<Suggestion>()
        val byExercise = feedback.filter { it.day >= today - 30 }.sortedBy { it.day }.groupBy { it.exerciseId }

        for (p in plans.filter { it.active && it.endDay == null && it.startDay <= today }) {
            p.items.forEachIndexed { i, item ->
                if (!p.includes(item)) return@forEachIndexed
                val e = exercises[item.exerciseId] ?: return@forEachIndexed
                val recent = byExercise[e.id].orEmpty()
                val dose = item.dose ?: e.defaultDose
                val easy = recent.size >= EASY_STREAK && recent.takeLast(EASY_STREAK).all { it.feedback == Feedback.TOO_EASY }
                val hard = recent.size >= HARD_STREAK &&
                    recent.takeLast(HARD_STREAK).all { it.feedback == Feedback.TOO_HARD || it.feedback == Feedback.HURT }
                when {
                    easy -> {
                        val next = e.progressions.firstNotNullOfOrNull { exercises[it] }
                        val key = "harder:${p.plan.id}:${e.id}"
                        if (next != null) {
                            out += Suggestion(
                                key, true,
                                "${e.name} feels easy: try ${next.name}?",
                                "You rated it \"too easy\" $EASY_STREAK times in a row. In ${p.plan.name}, swap it for the harder version.",
                                SuggestionAction.Swap(p.id, i, next.id),
                            )
                        } else {
                            bump(dose, e)?.let { more ->
                                out += Suggestion(
                                    key, true,
                                    "${e.name} feels easy: do a bit more?",
                                    "You rated it \"too easy\" $EASY_STREAK times in a row. In ${p.plan.name}, go to " +
                                        doseText(more, e.measure, e.laterality) + ".",
                                    SuggestionAction.ChangeDose(p.id, i, more),
                                )
                            }
                        }
                    }
                    hard -> {
                        val easier = e.regressions.firstNotNullOfOrNull { exercises[it] }
                        val key = "easier:${p.plan.id}:${e.id}"
                        if (easier != null) {
                            out += Suggestion(
                                key, false,
                                "${e.name} feels too much: try ${easier.name}?",
                                "Your last ratings were \"too hard\" or \"hurt\". In ${p.plan.name}, swap it for the easier version for now.",
                                SuggestionAction.Swap(p.id, i, easier.id),
                            )
                        } else {
                            reduce(dose, e)?.let { less ->
                                out += Suggestion(
                                    key, false,
                                    "${e.name} feels too much: ease off?",
                                    "Your last ratings were \"too hard\" or \"hurt\". In ${p.plan.name}, drop to " +
                                        doseText(less, e.measure, e.laterality) + " for now.",
                                    SuggestionAction.ChangeDose(p.id, i, less),
                                )
                            }
                        }
                    }
                }
            }

            // Phase progression.
            val phaseIndex = p.phaseIndex
            val next = p.plan.phases.getOrNull(phaseIndex + 1)
            if (phaseIndex >= 0 && next != null && today - p.startDay >= MIN_DAYS_IN_PHASE) {
                val points = checkInsByPlan[p.plan.id].orEmpty().sortedBy { it.day }
                val symptomsOk = p.plan.trackedMetric == null || run {
                    val last = points.lastOrNull()
                    last != null && last.day >= today - 10 && last.value <= PHASE_SYMPTOM_MAX &&
                        (points.size < 2 || last.value <= points[points.size - 2].value)
                }
                val adherence = adherenceByPlan[p.plan.id]
                if (symptomsOk && adherence != null && adherence >= PHASE_ADHERENCE_MIN) {
                    val current = p.plan.phases[phaseIndex]
                    out += Suggestion(
                        "phase:${p.plan.id}:${next.id}", true,
                        "${p.plan.name}: ready for phase ${phaseIndex + 2}?",
                        "You've been steady in \"${current.name}\"" +
                            (if (p.plan.trackedMetric != null) " and your symptoms are low" else "") +
                            ". Move on when: ${current.exitCriteria ?: "you feel ready"}",
                        SuggestionAction.NextPhase(p.id, next.id),
                    )
                }
            }
        }
        return out.filter { it.key !in dismissed }.distinctBy { it.key }
    }

    /** A little more: reps +2 (max 30, then a set), holds +10 s (max 120), time +30 s, distance +1 set. */
    fun bump(d: Dose, e: Exercise): Dose? = when (e.measure) {
        Measure.REPS -> {
            val reps = d.reps ?: return null
            if (reps + 2 <= 30) d.copy(reps = reps + 2) else if (d.sets < 5) d.copy(sets = d.sets + 1) else null
        }
        Measure.HOLD -> (d.holdSec ?: return null).let { if (it + 10 <= 120) d.copy(holdSec = it + 10) else null }
        Measure.REPS_WITH_HOLD -> {
            val hold = d.holdSec ?: return null
            val reps = d.reps ?: return null
            if (hold < 10) d.copy(holdSec = hold + 2) else if (reps + 2 <= 20) d.copy(reps = reps + 2) else null
        }
        Measure.TIME -> (d.durationSec ?: return null).let { if (it + 30 <= 3600) d.copy(durationSec = it + 30) else null }
        Measure.DISTANCE -> if (d.sets < 8) d.copy(sets = d.sets + 1) else null
    }

    /** A little less: a set off first, then reps -2 / holds -10 s / time -30 s. */
    fun reduce(d: Dose, e: Exercise): Dose? = when {
        d.sets > 1 -> d.copy(sets = d.sets - 1)
        e.measure == Measure.REPS && (d.reps ?: 0) > 4 -> d.copy(reps = d.reps!! - 2)
        e.measure == Measure.HOLD && (d.holdSec ?: 0) > 15 -> d.copy(holdSec = d.holdSec!! - 10)
        e.measure == Measure.REPS_WITH_HOLD && (d.reps ?: 0) > 4 -> d.copy(reps = d.reps!! - 2)
        e.measure == Measure.TIME && (d.durationSec ?: 0) > 60 -> d.copy(durationSec = d.durationSec!! - 30)
        else -> null
    }

    /** The dose to use when swapping [from]'s plan item to [to]: [to]'s default, keeping "sore side". */
    fun doseForSwap(old: Dose?, to: Exercise): Dose {
        val base = to.defaultDose
        return if (old?.sides == Sides.AFFECTED && to.laterality == com.mohit.rehabit.content.Laterality.EACH_SIDE) {
            base.copy(sides = Sides.AFFECTED)
        } else {
            base.copy(sides = base.sides.takeIf { to.laterality == com.mohit.rehabit.content.Laterality.EACH_SIDE })
        }
    }
}
