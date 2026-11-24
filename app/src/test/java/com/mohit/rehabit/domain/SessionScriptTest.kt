package com.mohit.rehabit.domain

import com.mohit.rehabit.content.BodyArea
import com.mohit.rehabit.content.DayRule
import com.mohit.rehabit.content.Dose
import com.mohit.rehabit.content.Exercise
import com.mohit.rehabit.content.ExerciseKind
import com.mohit.rehabit.content.Laterality
import com.mohit.rehabit.content.Measure
import com.mohit.rehabit.content.Position
import com.mohit.rehabit.content.Schedule
import com.mohit.rehabit.content.Sides
import com.mohit.rehabit.content.SlotId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionScriptTest {

    @Test
    fun eachSideHoldAlternatesSidesWithSwitchAndRest() {
        val e = exercise(Measure.HOLD, Laterality.EACH_SIDE)
        val steps = SessionScript.build(listOf(SessionItem(e, Dose(sets = 2, holdSec = 30, restSec = 10))))
        assertEquals(
            listOf(
                StepKind.PREPARE,
                StepKind.WORK, StepKind.SWITCH_SIDE, StepKind.WORK, StepKind.REST,
                StepKind.WORK, StepKind.SWITCH_SIDE, StepKind.WORK,
            ),
            steps.map { it.kind },
        )
        val work = steps.filter { it.kind == StepKind.WORK }
        assertEquals(listOf(Side.LEFT, Side.RIGHT, Side.LEFT, Side.RIGHT), work.map { it.side })
        assertEquals(listOf(1, 1, 2, 2), work.map { it.set })
        assertTrue(work.all { it.durationMs == 30_000L })
        assertEquals(10_000L, steps.first { it.kind == StepKind.REST }.durationMs)
    }

    @Test
    fun affectedSideOnlyDoesOneSide() {
        val e = exercise(Measure.HOLD, Laterality.EACH_SIDE)
        val steps = SessionScript.build(listOf(SessionItem(e, Dose(sets = 2, holdSec = 20, sides = Sides.AFFECTED))))
        assertEquals(listOf(StepKind.PREPARE, StepKind.WORK, StepKind.REST, StepKind.WORK), steps.map { it.kind })
        assertTrue(steps.filter { it.kind == StepKind.WORK }.all { it.side == Side.AFFECTED })
    }

    @Test
    fun zeroRestSkipsRestStep() {
        val e = exercise(Measure.HOLD, Laterality.BILATERAL)
        val steps = SessionScript.build(listOf(SessionItem(e, Dose(sets = 3, holdSec = 10, restSec = 0))))
        assertEquals(listOf(StepKind.PREPARE, StepKind.WORK, StepKind.WORK, StepKind.WORK), steps.map { it.kind })
    }

    @Test
    fun plainRepsAreOpenEnded() {
        val e = exercise(Measure.REPS, Laterality.BILATERAL)
        val work = SessionScript.build(listOf(SessionItem(e, Dose(reps = 12)))).single { it.kind == StepKind.WORK }
        assertNull(work.durationMs)
        assertEquals("12 reps", work.target)
    }

    @Test
    fun tempoRepsAreTimedWithMetronome() {
        val e = exercise(Measure.REPS, Laterality.BILATERAL)
        val o = ScriptOptions()
        val work = SessionScript.build(listOf(SessionItem(e, Dose(reps = 10, tempo = "2-1-3"))), o)
            .single { it.kind == StepKind.WORK }
        val cycle = 6_000L
        val offset = o.repIntroSec * 1000L
        assertEquals(offset + 10 * cycle, work.durationMs)
        assertEquals(RepCounter(10, cycle, offset), work.repCounter)
        // 3 metronome beeps per rep: one high (start) and two low.
        val highs = work.cues.count { it.action == CueAction.Beep(Tone.HIGH) && it.atMs >= offset }
        assertEquals(10, highs)
        assertTrue(work.cues.any { it.atMs == offset + 2_000L && it.action == CueAction.Beep(Tone.LOW) })
    }

    @Test
    fun repsWithHoldCountsAlternatingRepsForBothSides() {
        val e = exercise(Measure.REPS_WITH_HOLD, Laterality.ALTERNATING)
        val o = ScriptOptions()
        val work = SessionScript.build(listOf(SessionItem(e, Dose(reps = 6, holdSec = 8))), o)
            .single { it.kind == StepKind.WORK }
        val cycle = (8 + o.relaxSec) * 1000L
        assertEquals(RepCounter(12, cycle, o.repIntroSec * 1000L), work.repCounter)
        assertEquals(o.repIntroSec * 1000L + 12 * cycle, work.durationMs)
    }

    @Test
    fun repCounterProgresses() {
        val c = RepCounter(reps = 3, cycleMs = 1000, offsetMs = 500)
        assertEquals(0, c.currentRep(0))
        assertEquals(1, c.currentRep(500))
        assertEquals(2, c.currentRep(1600))
        assertEquals(3, c.currentRep(99_000))
    }

    @Test
    fun timedStepsEndWithCountdownBeeps() {
        val e = exercise(Measure.HOLD, Laterality.BILATERAL)
        val work = SessionScript.build(listOf(SessionItem(e, Dose(holdSec = 30)))).single { it.kind == StepKind.WORK }
        val lows = work.cues.filter { it.action == CueAction.Beep(Tone.LOW) }.map { it.atMs }
        assertEquals(listOf(27_000L, 28_000L, 29_000L), lows)
    }

    @Test
    fun prepareSpeaksNameAndCues() {
        val e = exercise(Measure.HOLD, Laterality.BILATERAL)
        val prepare = SessionScript.build(listOf(SessionItem(e, Dose(holdSec = 30)))).first()
        val speech = prepare.cues.mapNotNull { (it.action as? CueAction.Speak)?.text }.single()
        assertEquals("First: Test stretch. Cue one. Cue two.", speech)
    }

    @Test
    fun spokenDurations() {
        assertEquals("30 seconds", SessionScript.secondsSpeech(30))
        assertEquals("1 minute", SessionScript.secondsSpeech(60))
        assertEquals("1 minute 30 seconds", SessionScript.secondsSpeech(90))
        assertEquals("5 minutes", SessionScript.secondsSpeech(300))
    }

    @Test
    fun tempoParsing() {
        assertEquals(listOf(2, 1, 3), SessionScript.tempoPhases("2-1-3"))
        assertNull(SessionScript.tempoPhases(null))
        assertNull(SessionScript.tempoPhases("0-0"))
    }

    @Test
    fun scheduleTexts() {
        assertEquals("Daily · Night", scheduleText(Schedule(slots = listOf(SlotId.NIGHT))))
        assertEquals("2× a day · Morning, Night", scheduleText(Schedule(timesPerDay = 2, slots = listOf(SlotId.MORNING, SlotId.NIGHT))))
        assertEquals("2× a day · Desk break", scheduleText(Schedule(timesPerDay = 2, slots = listOf(SlotId.DESK, SlotId.DESK))))
        assertEquals(
            "Every other day · Night · not on run days",
            scheduleText(Schedule(days = DayRule.EVERY_N_DAYS, everyNDays = 2, slots = listOf(SlotId.NIGHT), notOnRunDay = true)),
        )
    }

    private fun exercise(measure: Measure, laterality: Laterality) = Exercise(
        id = "test",
        name = "Test stretch",
        bodyAreas = listOf(BodyArea.CALF),
        kind = ExerciseKind.STRETCH_STATIC,
        position = Position.STANDING,
        laterality = laterality,
        measure = measure,
        defaultDose = Dose(holdSec = 30, reps = 10),
        why = "w",
        setup = listOf("s"),
        cues = listOf("Cue one", "Cue two"),
        feel = "f",
        stopIf = "s",
    )
}
