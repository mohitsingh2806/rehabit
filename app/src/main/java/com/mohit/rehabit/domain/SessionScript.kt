package com.mohit.rehabit.domain

import com.mohit.rehabit.content.Dose
import com.mohit.rehabit.content.Exercise
import com.mohit.rehabit.content.Laterality
import com.mohit.rehabit.content.Measure
import com.mohit.rehabit.content.Sides

/** One exercise in a session, with the dose to do. */
data class SessionItem(val exercise: Exercise, val dose: Dose)

enum class StepKind { PREPARE, WORK, SWITCH_SIDE, REST }

enum class Side(val label: String) { LEFT("Left"), RIGHT("Right"), AFFECTED("Sore") }

enum class Tone { HIGH, LOW }

sealed interface CueAction {
    data class Speak(val text: String) : CueAction
    data class Beep(val tone: Tone) : CueAction
    data object Buzz : CueAction
}

/** Something the player does [atMs] after the step starts. */
data class Cue(val atMs: Long, val action: CueAction)

/** Auto-counted reps: rep n (1-based) starts at offsetMs + (n - 1) * cycleMs. */
data class RepCounter(val reps: Int, val cycleMs: Long, val offsetMs: Long) {
    /** 0 before the first rep starts, then 1..reps. */
    fun currentRep(elapsedMs: Long): Int =
        if (elapsedMs < offsetMs) 0 else ((elapsedMs - offsetMs) / cycleMs + 1).toInt().coerceAtMost(reps)
}

data class Step(
    val kind: StepKind,
    /** Index into the session's items. */
    val itemIndex: Int,
    val exercise: Exercise,
    val dose: Dose,
    /** 1-based set number; 0 for PREPARE. */
    val set: Int,
    val sets: Int,
    val side: Side?,
    /** Big line on the player, e.g. "Get ready", "Left side · Hold", "Rest". */
    val label: String,
    /** Null = open-ended: the user taps Done. */
    val durationMs: Long?,
    val repCounter: RepCounter? = null,
    /** Open-ended work target, e.g. "12 reps" or "80 m". */
    val target: String? = null,
    val cues: List<Cue> = emptyList(),
)

data class ScriptOptions(
    val prepareSec: Int = 10,
    val switchSec: Int = 5,
    /** Rest between sets when the dose doesn't say. */
    val defaultRestSec: Int = 15,
    /** Relax time between reps of a REPS_WITH_HOLD exercise. */
    val relaxSec: Int = 2,
    /** Time to listen to the instructions before auto-counted reps start. */
    val repIntroSec: Int = 4,
    /** Estimates for open-ended steps. */
    val estimatedSecPerRep: Int = 3,
    val estimatedMetersPerSec: Int = 3,
)

/**
 * Expands exercises + doses into a timeline of player steps. Pure and deterministic, so the player
 * only has to play steps, and timings, sides, rests and spoken lines are unit-tested.
 *
 * Per exercise: PREPARE (name + cues spoken), then for each set: WORK per side (SWITCH_SIDE
 * between sides), and REST between sets. Countdown beeps sound in the last 3 seconds of timed steps.
 */
object SessionScript {

    fun build(items: List<SessionItem>, o: ScriptOptions = ScriptOptions()): List<Step> {
        val steps = mutableListOf<Step>()
        items.forEachIndexed { i, item ->
            val e = item.exercise
            val d = item.dose
            val sets = d.sets.coerceAtLeast(1)
            val sides: List<Side?> = when {
                e.laterality != Laterality.EACH_SIDE -> listOf(null)
                d.sides == Sides.AFFECTED -> listOf(Side.AFFECTED)
                else -> listOf(Side.LEFT, Side.RIGHT)
            }

            val prepareMs = o.prepareSec * 1000L
            val intro = (if (i == 0) "First: " else "Next: ") + e.name + ". " + e.cues.joinToString(". ") + "."
            steps += Step(
                kind = StepKind.PREPARE, itemIndex = i, exercise = e, dose = d, set = 0, sets = sets,
                side = sides.first(), label = "Get ready", durationMs = prepareMs,
                cues = listOf(Cue(0, CueAction.Speak(intro))) + countdown(prepareMs),
            )

            for (set in 1..sets) {
                sides.forEachIndexed { si, side ->
                    if (si > 0) {
                        val ms = o.switchSec * 1000L
                        steps += Step(
                            kind = StepKind.SWITCH_SIDE, itemIndex = i, exercise = e, dose = d, set = set,
                            sets = sets, side = side, label = "Switch sides", durationMs = ms,
                            cues = listOf(Cue(0, CueAction.Buzz), Cue(0, CueAction.Speak("Switch sides."))) + countdown(ms),
                        )
                    }
                    steps += work(i, e, d, set, sets, side, o)
                }
                if (set < sets) {
                    val restSec = d.restSec ?: o.defaultRestSec
                    if (restSec > 0) {
                        val ms = restSec * 1000L
                        steps += Step(
                            kind = StepKind.REST, itemIndex = i, exercise = e, dose = d, set = set, sets = sets,
                            side = null, label = "Rest", durationMs = ms,
                            cues = listOf(Cue(0, CueAction.Buzz), Cue(0, CueAction.Speak("Rest. ${secondsSpeech(restSec)}."))) +
                                countdown(ms),
                        )
                    }
                }
            }
        }
        return steps
    }

    private fun work(i: Int, e: Exercise, d: Dose, set: Int, sets: Int, side: Side?, o: ScriptOptions): Step {
        val sideSpeech = side?.let { "${it.label} side. " } ?: ""
        val sideLabel = side?.let { "${it.label} side · " } ?: ""
        val alternating = e.laterality == Laterality.ALTERNATING
        val start = listOf(Cue(0, CueAction.Buzz), Cue(0, CueAction.Beep(Tone.HIGH)))

        fun step(label: String, durationMs: Long?, speech: String, extra: List<Cue> = emptyList(),
                 counter: RepCounter? = null, target: String? = null) = Step(
            kind = StepKind.WORK, itemIndex = i, exercise = e, dose = d, set = set, sets = sets, side = side,
            label = label, durationMs = durationMs, repCounter = counter, target = target,
            cues = start + Cue(0, CueAction.Speak(speech)) + extra + (durationMs?.let { countdown(it) } ?: emptyList()),
        )

        return when (e.measure) {
            Measure.HOLD -> {
                val sec = d.holdSec ?: 30
                step("${sideLabel}Hold", sec * 1000L, "${sideSpeech}Hold ${secondsSpeech(sec)}.")
            }
            Measure.TIME -> {
                val sec = d.durationSec ?: 60
                step("${sideLabel}Go", sec * 1000L, "$sideSpeech${secondsSpeech(sec)}. Go.")
            }
            Measure.DISTANCE -> {
                val m = d.distanceM ?: 20
                step("${sideLabel}Go", null, "$sideSpeech$m metres. Tap done at the end.", target = distanceText(m))
            }
            Measure.REPS -> {
                val reps = (d.reps ?: 10) * (if (alternating) 2 else 1)
                val phases = tempoPhases(d.tempo)
                val alt = if (alternating) ", alternating sides" else ""
                if (phases == null) {
                    step(
                        "${sideLabel}Reps", null, "$sideSpeech${d.reps ?: 10} reps$alt. Tap done when you finish.",
                        target = "${d.reps ?: 10} reps" + if (alternating) " each side" else "",
                    )
                } else {
                    // Metronome: high beep starts each rep, low beep marks each later phase.
                    val cycle = phases.sum() * 1000L
                    val offset = o.repIntroSec * 1000L
                    val cues = (0 until reps).flatMap { r ->
                        val t0 = offset + r * cycle
                        var t = t0
                        phases.mapIndexed { p, sec ->
                            val cue = Cue(t, CueAction.Beep(if (p == 0) Tone.HIGH else Tone.LOW))
                            t += sec * 1000L
                            cue
                        }
                    }
                    step(
                        "${sideLabel}Slow reps", offset + reps * cycle,
                        "$sideSpeech$reps slow reps$alt. Follow the beeps.", cues, RepCounter(reps, cycle, offset),
                    )
                }
            }
            Measure.REPS_WITH_HOLD -> {
                val reps = (d.reps ?: 10) * (if (alternating) 2 else 1)
                val hold = d.holdSec ?: 5
                val cycle = (hold + o.relaxSec) * 1000L
                val offset = o.repIntroSec * 1000L
                val cues = (0 until reps).flatMap { r ->
                    val t = offset + r * cycle
                    listOf(Cue(t, CueAction.Beep(Tone.HIGH)), Cue(t + hold * 1000L, CueAction.Beep(Tone.LOW)))
                }
                val alt = if (alternating) ", alternating sides" else ""
                step(
                    "${sideLabel}Hold each rep", offset + reps * cycle,
                    "$sideSpeech$reps reps$alt. Hold ${secondsSpeech(hold)} on the high beep, relax on the low beep.",
                    cues, RepCounter(reps, cycle, offset),
                )
            }
        }
    }

    /** Low beeps 3, 2 and 1 seconds before a timed step ends. */
    private fun countdown(durationMs: Long): List<Cue> =
        if (durationMs < 4000) emptyList() else (3 downTo 1).map { Cue(durationMs - it * 1000L, CueAction.Beep(Tone.LOW)) }

    /** "2-1-3" → [2, 1, 3]; null when absent or zero-length. */
    fun tempoPhases(tempo: String?): List<Int>? {
        val parts = tempo?.split('-')?.mapNotNull { it.trim().toIntOrNull() } ?: return null
        return parts.takeIf { it.size >= 2 && it.sum() > 0 }
    }

    /** Rough total time, using estimates for open-ended steps. */
    fun estimateMs(steps: List<Step>, o: ScriptOptions = ScriptOptions()): Long = steps.sumOf { s ->
        s.durationMs ?: when (s.exercise.measure) {
            Measure.DISTANCE -> ((s.dose.distanceM ?: 20) / o.estimatedMetersPerSec) * 1000L
            else -> {
                val reps = (s.dose.reps ?: 10) * (if (s.exercise.laterality == Laterality.ALTERNATING) 2 else 1)
                reps * o.estimatedSecPerRep * 1000L
            }
        }
    }

    /** 30 → "30 seconds", 60 → "1 minute", 90 → "1 minute 30 seconds". */
    fun secondsSpeech(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        val min = when (m) { 0 -> null; 1 -> "1 minute"; else -> "$m minutes" }
        val secs = when (s) { 0 -> null; 1 -> "1 second"; else -> "$s seconds" }
        return listOfNotNull(min, secs).joinToString(" ").ifEmpty { "0 seconds" }
    }
}
