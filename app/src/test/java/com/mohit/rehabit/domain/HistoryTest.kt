package com.mohit.rehabit.domain

import com.mohit.rehabit.content.BodyArea
import com.mohit.rehabit.content.Dose
import com.mohit.rehabit.content.Exercise
import com.mohit.rehabit.content.ExerciseKind
import com.mohit.rehabit.content.Laterality
import com.mohit.rehabit.content.Measure
import com.mohit.rehabit.content.Position
import com.mohit.rehabit.content.SlotId
import com.mohit.rehabit.data.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HistoryTest {

    private val monday = LocalDate.of(2026, 11, 23).toEpochDay()
    private val ex = Exercise(
        id = "e", name = "E", bodyAreas = listOf(BodyArea.CALF), kind = ExerciseKind.STRENGTH,
        position = Position.STANDING, laterality = Laterality.BILATERAL, measure = Measure.REPS,
        defaultDose = Dose(reps = 10), why = "w", setup = listOf("s"), cues = listOf("c"), feel = "f", stopIf = "s",
    )

    private fun task(enrollment: Long, status: TaskStatus?, core: Boolean = true, item: Int = 0) = Task(
        exercise = ex, dose = ex.defaultDose, affected = null, core = core, slot = SlotKey(SlotId.NIGHT),
        refs = listOf(TaskRef(enrollment, item, 0)), planNames = listOf("P"), rehab = true, moved = false,
        notes = emptyList(), status = status,
    )

    private fun agenda(day: Long, vararg tasks: Task) =
        Agenda(day, listOf(SlotGroup(SlotKey(SlotId.NIGHT), 21 * 60, tasks.toList())), runDay = false, coreOnly = false, hiddenOptional = 0)

    @Test
    fun dayMarks() {
        val today = monday + 3
        assertEquals(DayMark.ALL_DONE, History.score(agenda(monday, task(1, TaskStatus.DONE)), false, today).mark)
        assertEquals(DayMark.PARTIAL, History.score(agenda(monday, task(1, TaskStatus.DONE), task(1, null, item = 1)), false, today).mark)
        assertEquals(DayMark.MISSED, History.score(agenda(monday, task(1, null)), false, today).mark)
        assertEquals(DayMark.MISSED, History.score(agenda(monday, task(1, TaskStatus.SKIPPED)), false, today).mark)
        assertEquals(DayMark.EXCUSED, History.score(agenda(monday, task(1, null)), true, today).mark)
        assertEquals(DayMark.NOTHING, History.score(agenda(monday), false, today).mark)
        assertEquals(DayMark.IN_PROGRESS, History.score(agenda(today, task(1, null)), false, today).mark)
        assertEquals(DayMark.FUTURE, History.score(agenda(today + 1, task(1, null)), false, today).mark)
    }

    @Test
    fun optionalItemsNeverHurtTheScore() {
        val score = History.score(agenda(monday, task(1, TaskStatus.DONE), task(1, null, core = false, item = 1)), false, monday + 1)
        assertEquals(DayMark.ALL_DONE, score.mark)
        assertEquals(1, score.planned)
    }

    @Test
    fun partlyDoneCountsHalf() {
        val score = History.score(agenda(monday, task(1, TaskStatus.PARTIAL), task(1, TaskStatus.DONE, item = 1)), false, monday + 1)
        assertEquals(1.5, score.done, 0.001)
    }

    @Test
    fun weeklyAdherenceSkipsTodayAndExcusedDaysAndFiltersByPlan() {
        val today = monday + 2 // Wednesday
        val agendas = listOf(
            agenda(monday, task(1, TaskStatus.DONE), task(2, null)),        // plan 1: 1/1
            agenda(monday + 1, task(1, null)),                               // excused
            agenda(today, task(1, null)),                                    // today: left out
            agenda(monday - 7, task(1, TaskStatus.PARTIAL), task(1, null, item = 1)), // last week: 0.5/2
        )
        val weeks = History.weekly(setOf(1L), agendas, excused = setOf(monday + 1), today = today)
        assertEquals(listOf(monday - 7, monday), weeks.map { it.weekStart })
        assertEquals(25, weeks[0].percent)
        assertEquals(100, weeks[1].percent)
        assertNull(WeekAdherence(monday, 0, 0.0).percent)
    }

    @Test
    fun weekStartsOnMonday() {
        assertEquals(monday, History.weekStart(monday + 6))
        assertEquals(monday, History.weekStart(monday))
    }

    @Test
    fun risingCheckInsWarn() {
        val today = monday + 7
        val insights = Insights.forPlan("p", "Neck", listOf(CheckInPoint(monday, 3), CheckInPoint(monday + 5, 5)), 0, today)
        assertEquals(listOf(InsightKind.WARNING), insights.map { it.kind })
        val threeUp = Insights.forPlan(
            "p", "Neck",
            listOf(CheckInPoint(monday, 3), CheckInPoint(monday + 2, 4), CheckInPoint(monday + 4, 5)), 0, today,
        )
        assertTrue(threeUp.any { it.kind == InsightKind.WARNING })
    }

    @Test
    fun steadyCheckInsSayNothing() {
        val insights = Insights.forPlan("p", "Neck", listOf(CheckInPoint(monday, 4), CheckInPoint(monday + 2, 5)), 0, monday + 3)
        assertTrue(insights.isEmpty())
    }

    @Test
    fun improvementIsCelebrated() {
        val today = monday + 30
        val points = listOf(CheckInPoint(monday, 7), CheckInPoint(monday + 14, 5), CheckInPoint(monday + 28, 3))
        val insights = Insights.forPlan("p", "Heel", points, 0, today)
        assertEquals(listOf(InsightKind.GOOD), insights.map { it.kind })
        assertTrue(insights.single().title.contains("7 to 3"))
    }

    @Test
    fun repeatedHurtWarns() {
        assertTrue(Insights.forPlan("p", "Knee", emptyList(), 3, monday).single().kind == InsightKind.WARNING)
        assertTrue(Insights.forPlan("p", "Knee", emptyList(), 2, monday).isEmpty())
    }

    @Test
    fun painfulRecentSessionWarnsOnlyWhenHighAndRecent() {
        assertEquals(InsightKind.WARNING, Insights.sessionPain(7, monday, monday + 1)?.kind)
        assertNull(Insights.sessionPain(5, monday, monday))
        assertNull(Insights.sessionPain(8, monday, monday + 5))
    }
}
