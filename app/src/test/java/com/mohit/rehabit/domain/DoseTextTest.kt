package com.mohit.rehabit.domain

import com.mohit.rehabit.content.Dose
import com.mohit.rehabit.content.Laterality
import com.mohit.rehabit.content.Measure
import com.mohit.rehabit.content.Sides
import org.junit.Assert.assertEquals
import org.junit.Test

class DoseTextTest {

    @Test
    fun holdEachSide() {
        assertEquals("2 × 30 s · each side", doseText(Dose(sets = 2, holdSec = 30), Measure.HOLD, Laterality.EACH_SIDE))
    }

    @Test
    fun singleHold() {
        assertEquals("45 s hold", doseText(Dose(holdSec = 45), Measure.HOLD, Laterality.BILATERAL))
    }

    @Test
    fun repsWithHold() {
        assertEquals("10 reps · 3 s hold", doseText(Dose(reps = 10, holdSec = 3), Measure.REPS_WITH_HOLD, Laterality.BILATERAL))
        assertEquals(
            "2 × 10 · 3 s hold",
            doseText(Dose(sets = 2, reps = 10, holdSec = 3), Measure.REPS_WITH_HOLD, Laterality.BILATERAL),
        )
    }

    @Test
    fun repsWithTempo() {
        assertEquals(
            "3 × 12 · tempo 2-1-3",
            doseText(Dose(sets = 3, reps = 12, tempo = "2-1-3"), Measure.REPS, Laterality.BILATERAL),
        )
    }

    @Test
    fun affectedSideAndAlternating() {
        assertEquals(
            "2 × 1 min · sore side",
            doseText(Dose(sets = 2, durationSec = 60, sides = Sides.AFFECTED), Measure.TIME, Laterality.EACH_SIDE),
        )
        assertEquals(
            "6 reps · 8 s hold · each side, alternating",
            doseText(Dose(reps = 6, holdSec = 8), Measure.REPS_WITH_HOLD, Laterality.ALTERNATING),
        )
    }

    @Test
    fun durationsAndDistances() {
        assertEquals("45 s", durationText(45))
        assertEquals("1 min", durationText(60))
        assertEquals("1 min 30 s", durationText(90))
        assertEquals("80 m", distanceText(80))
        assertEquals("1 km", distanceText(1000))
        assertEquals("1.5 km", distanceText(1500))
        assertEquals("4 × 80 m", doseText(Dose(sets = 4, distanceM = 80), Measure.DISTANCE, Laterality.BILATERAL))
    }
}
