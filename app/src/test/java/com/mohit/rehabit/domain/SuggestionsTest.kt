package com.mohit.rehabit.domain

import com.mohit.rehabit.content.BodyArea
import com.mohit.rehabit.content.Dose
import com.mohit.rehabit.content.Exercise
import com.mohit.rehabit.content.ExerciseKind
import com.mohit.rehabit.content.Laterality
import com.mohit.rehabit.content.Measure
import com.mohit.rehabit.content.Phase
import com.mohit.rehabit.content.Plan
import com.mohit.rehabit.content.PlanCategory
import com.mohit.rehabit.content.PlanItem
import com.mohit.rehabit.content.PlanKind
import com.mohit.rehabit.content.Position
import com.mohit.rehabit.content.Schedule
import com.mohit.rehabit.content.Sides
import com.mohit.rehabit.content.SlotId
import com.mohit.rehabit.data.Feedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionsTest {

    private val today = 1000L
    private fun ex(id: String, measure: Measure = Measure.REPS, dose: Dose = Dose(sets = 3, reps = 10), prog: List<String> = emptyList(), reg: List<String> = emptyList(), lat: Laterality = Laterality.BILATERAL) = Exercise(
        id = id, name = id, bodyAreas = listOf(BodyArea.KNEE), kind = ExerciseKind.STRENGTH, position = Position.STANDING,
        laterality = lat, measure = measure, defaultDose = dose, why = "w", setup = listOf("s"), cues = listOf("c"),
        feel = "f", stopIf = "s", progressions = prog, regressions = reg,
    )
    private val easy = ex("easy")
    private val mid = ex("mid", prog = listOf("hard"), reg = listOf("easy"))
    private val hard = ex("hard")
    private val solo = ex("solo")
    private val exercises = listOf(easy, mid, hard, solo).associateBy { it.id }
    private val night = Schedule(slots = listOf(SlotId.NIGHT))

    private fun follow(vararg ids: String, phases: Int = 1, startDay: Long = today - 30, metric: String? = null): FollowedPlan {
        val items = ids.map { PlanItem(it, schedule = night) }
        val plan = Plan(
            id = "p", name = "Knee", kind = PlanKind.FOCUSED, category = PlanCategory.REHAB, goal = "g", whoFor = "w",
            estMinutes = 10, trackedMetric = metric,
            phases = (1..phases).map { Phase(id = "ph$it", name = "Phase $it", exitCriteria = "Feels easy", items = items) },
        )
        return FollowedPlan(1, plan, "ph1", items, startDay)
    }

    private fun fb(id: String, vararg f: Feedback) = f.mapIndexed { i, x -> FeedbackPoint(id, x, today - 10 + i) }

    private fun run(p: FollowedPlan, feedback: List<FeedbackPoint>, checkIns: List<CheckInPoint> = emptyList(), adherence: Int? = null, dismissed: Set<String> = emptySet()) =
        Suggestions.build(listOf(p), exercises, feedback, mapOf("p" to checkIns), mapOf("p" to adherence), dismissed, today)

    @Test
    fun threeTooEasySuggestsTheHarderVersion() {
        val s = run(follow("mid"), fb("mid", Feedback.TOO_EASY, Feedback.TOO_EASY, Feedback.TOO_EASY)).single()
        assertEquals(SuggestionAction.Swap(1, 0, "hard"), s.action)
        assertEquals(true, s.harder)
    }

    @Test
    fun twoEasyIsNotEnough() {
        assertTrue(run(follow("mid"), fb("mid", Feedback.RIGHT, Feedback.TOO_EASY, Feedback.TOO_EASY)).isEmpty())
    }

    @Test
    fun hurtOrTooHardSuggestsTheEasierVersion() {
        val s = run(follow("mid"), fb("mid", Feedback.TOO_HARD, Feedback.HURT)).single()
        assertEquals(SuggestionAction.Swap(1, 0, "easy"), s.action)
    }

    @Test
    fun noLinkedVersionMeansDoseChange() {
        val more = run(follow("solo"), fb("solo", Feedback.TOO_EASY, Feedback.TOO_EASY, Feedback.TOO_EASY)).single()
        assertEquals(SuggestionAction.ChangeDose(1, 0, Dose(sets = 3, reps = 12)), more.action)
        val less = run(follow("solo"), fb("solo", Feedback.HURT, Feedback.HURT)).single()
        assertEquals(SuggestionAction.ChangeDose(1, 0, Dose(sets = 2, reps = 10)), less.action)
    }

    @Test
    fun dismissedSuggestionsStayHidden() {
        val f = fb("mid", Feedback.TOO_EASY, Feedback.TOO_EASY, Feedback.TOO_EASY)
        assertTrue(run(follow("mid"), f, dismissed = setOf("harder:p:mid")).isEmpty())
    }

    @Test
    fun nextPhaseNeedsTimeLowSymptomsAndAdherence() {
        val low = listOf(CheckInPoint(today - 7, 4), CheckInPoint(today - 2, 2))
        val ready = run(follow("solo", phases = 2, metric = "Knee pain"), emptyList(), low, adherence = 80).single()
        assertEquals(SuggestionAction.NextPhase(1, "ph2"), ready.action)
        assertTrue(ready.text.contains("Feels easy"))

        assertTrue(run(follow("solo", phases = 2, metric = "Knee pain", startDay = today - 5), emptyList(), low, 80).isEmpty())
        assertTrue(run(follow("solo", phases = 2, metric = "Knee pain"), emptyList(), low, 50).isEmpty())
        val rising = listOf(CheckInPoint(today - 7, 1), CheckInPoint(today - 2, 3))
        assertTrue(run(follow("solo", phases = 2, metric = "Knee pain"), emptyList(), rising, 80).isEmpty())
        assertTrue(run(follow("solo", phases = 1, metric = "Knee pain"), emptyList(), low, 80).isEmpty()) // last phase
    }

    @Test
    fun doseSteps() {
        val hold = ex("h", Measure.HOLD, Dose(sets = 1, holdSec = 30))
        assertEquals(40, Suggestions.bump(hold.defaultDose, hold)?.holdSec)
        assertEquals(20, Suggestions.reduce(hold.defaultDose, hold)?.holdSec)
        assertNull(Suggestions.reduce(Dose(sets = 1, holdSec = 10), hold))
        val repsHold = ex("rh", Measure.REPS_WITH_HOLD, Dose(sets = 1, reps = 10, holdSec = 5))
        assertEquals(7, Suggestions.bump(repsHold.defaultDose, repsHold)?.holdSec)
    }

    @Test
    fun swapKeepsSoreSideWhenPossible() {
        val one = ex("one", lat = Laterality.EACH_SIDE, dose = Dose(sets = 2, reps = 10))
        assertEquals(Sides.AFFECTED, Suggestions.doseForSwap(Dose(reps = 5, sides = Sides.AFFECTED), one).sides)
        assertNull(Suggestions.doseForSwap(Dose(reps = 5, sides = Sides.AFFECTED), solo).sides)
    }
}
