package com.mohit.rehabit.domain

import com.mohit.rehabit.content.Dose
import com.mohit.rehabit.content.Laterality
import com.mohit.rehabit.content.Measure
import com.mohit.rehabit.content.Sides

/**
 * One-line dose summary shown in lists, detail screens and the player, e.g.
 * "2 × 30 s · each side", "10 reps · 3 s hold", "3 × 12 · tempo 2-1-2", "5 min".
 */
fun doseText(dose: Dose, measure: Measure, laterality: Laterality): String {
    val sets = dose.sets
    val main = when (measure) {
        Measure.REPS -> {
            val reps = dose.reps ?: 0
            if (sets > 1) "$sets × $reps" else "$reps reps"
        }
        Measure.HOLD -> {
            val hold = durationText(dose.holdSec ?: 0)
            if (sets > 1) "$sets × $hold" else "$hold hold"
        }
        Measure.REPS_WITH_HOLD -> {
            val reps = dose.reps ?: 0
            val hold = durationText(dose.holdSec ?: 0)
            if (sets > 1) "$sets × $reps · $hold hold" else "$reps reps · $hold hold"
        }
        Measure.TIME -> {
            val time = durationText(dose.durationSec ?: 0)
            if (sets > 1) "$sets × $time" else time
        }
        Measure.DISTANCE -> {
            val distance = distanceText(dose.distanceM ?: 0)
            if (sets > 1) "$sets × $distance" else distance
        }
    }
    val side = when (laterality) {
        Laterality.BILATERAL -> null
        Laterality.EACH_SIDE -> if (dose.sides == Sides.AFFECTED) "sore side" else "each side"
        Laterality.ALTERNATING -> "each side, alternating"
    }
    val breathing = measure == Measure.HOLD || measure == Measure.TIME
    val tempo = dose.tempo?.let { t ->
        val parts = t.split('-')
        when {
            !breathing -> "tempo $t"
            parts.size == 2 -> "breathe in ${parts[0]}, out ${parts[1]}"
            else -> "breathing $t"
        }
    }
    return listOfNotNull(main, side, tempo).joinToString(" · ")
}

/** 45 → "45 s", 60 → "1 min", 90 → "1 min 30 s". */
fun durationText(seconds: Int): String = when {
    seconds < 60 -> "$seconds s"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "${seconds / 60} min ${seconds % 60} s"
}

/** 80 → "80 m", 1000 → "1 km", 1500 → "1.5 km". */
fun distanceText(meters: Int): String = when {
    meters < 1000 -> "$meters m"
    meters % 1000 == 0 -> "${meters / 1000} km"
    else -> "${meters / 1000}.${(meters % 1000) / 100} km"
}
