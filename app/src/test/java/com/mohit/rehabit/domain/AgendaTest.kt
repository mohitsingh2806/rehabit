package com.mohit.rehabit.domain

import com.mohit.rehabit.content.BodyArea
import com.mohit.rehabit.content.DayRule
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
import com.mohit.rehabit.data.ReminderMode
import com.mohit.rehabit.data.SideChoice
import com.mohit.rehabit.data.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AgendaTest {

    private val monday = LocalDate.of(2026, 11, 23).toEpochDay()
    private val prefs = DayPrefs(morningMin = 7 * 60, nightMin = 21 * 60, desk1Min = 11 * 60, desk2Min = 15 * 60)
    private val calf = exercise("calf", Laterality.EACH_SIDE, Measure.HOLD, Dose(sets = 2, holdSec = 30))
    private val raise = exercise("raise", Laterality.BILATERAL, Measure.REPS, Dose(sets = 3, reps = 12))
    private val tuck = exercise("tuck", Laterality.BILATERAL, Measure.REPS_WITH_HOLD, Dose(reps = 10, holdSec = 3))
    private val exercises = listOf(calf, raise, tuck).associateBy { it.id }

    @Test
    fun everyOtherDayIsAFixedCalendarFromTheStartDay() {
        val s = Schedule(days = DayRule.EVERY_N_DAYS, everyNDays = 2, slots = listOf(SlotId.NIGHT))
        assertTrue(AgendaBuilder.scheduledOn(s, monday, monday))
        assertFalse(AgendaBuilder.scheduledOn(s, monday + 1, monday))
        assertTrue(AgendaBuilder.scheduledOn(s, monday + 2, monday))
        assertFalse(AgendaBuilder.scheduledOn(s, monday - 2, monday)) // before the start
    }

    @Test
    fun repeatedSlotsBecomeSeparateInstancesInTimeOrder() {
        val plan = followed(1, PlanItem("tuck", schedule = Schedule(timesPerDay = 2, slots = listOf(SlotId.DESK, SlotId.DESK))))
        val agenda = build(listOf(plan))
        assertEquals(listOf(SlotKey(SlotId.DESK, 0), SlotKey(SlotId.DESK, 1)), agenda.groups.map { it.key })
        assertEquals(listOf(11 * 60, 15 * 60), agenda.groups.map { it.minute })
        assertEquals(listOf(TaskRef(1, 0, 0)), agenda.groups[0].tasks.single().refs)
        assertEquals(listOf(TaskRef(1, 0, 1)), agenda.groups[1].tasks.single().refs)
    }

    @Test
    fun sameExerciseInSameSlotFromTwoPlansIsMergedWithTheBiggerDose() {
        val a = followed(1, PlanItem("calf", dose = Dose(sets = 1, holdSec = 30), schedule = night()))
        val b = followed(2, PlanItem("calf", dose = Dose(sets = 3, holdSec = 30), schedule = night()))
        val task = build(listOf(a, b)).tasks.single()
        assertEquals(3, task.dose.sets)
        assertEquals(listOf(TaskRef(1, 0, 0), TaskRef(2, 0, 0)), task.refs)
    }

    @Test
    fun loggedStatusShowsAndAnyRefCounts() {
        val a = followed(1, PlanItem("calf", schedule = night()))
        val b = followed(2, PlanItem("calf", schedule = night()))
        val agenda = build(listOf(a, b), logs = mapOf(TaskRef(2, 0, 0) to TaskStatus.DONE))
        assertEquals(TaskStatus.DONE, agenda.tasks.single().status)
        assertNull(agenda.hero)
    }

    @Test
    fun heavyWorkMovesToTheNextDayAfterARun() {
        val heavy = Schedule(days = DayRule.EVERY_N_DAYS, everyNDays = 2, slots = listOf(SlotId.NIGHT), notOnRunDay = true)
        val plan = followed(1, PlanItem("raise", schedule = heavy))
        // Scheduled Monday, but Monday was a run day: gone Monday, moved to Tuesday.
        assertTrue(build(listOf(plan), runDays = setOf(monday)).tasks.isEmpty())
        val tuesday = build(listOf(plan), day = monday + 1, runDays = setOf(monday)).tasks.single()
        assertTrue(tuesday.moved)
        // No run: normal on Monday, nothing on Tuesday.
        assertFalse(build(listOf(plan)).tasks.single().moved)
        assertTrue(build(listOf(plan), day = monday + 1).tasks.isEmpty())
    }

    @Test
    fun heavyWorkAlreadyDoneBeforeTheRunStays() {
        val heavy = Schedule(slots = listOf(SlotId.NIGHT), notOnRunDay = true)
        val plan = followed(1, PlanItem("raise", schedule = heavy))
        val agenda = build(listOf(plan), runDays = setOf(monday), logs = mapOf(TaskRef(1, 0, 0) to TaskStatus.DONE))
        assertEquals(TaskStatus.DONE, agenda.tasks.single().status)
    }

    @Test
    fun chooserAnswersPickItems() {
        val items = arrayOf(
            PlanItem("calf", schedule = night()),
            PlanItem("raise", schedule = night(), showIf = "eases:bending"),
            PlanItem("tuck", schedule = night(), showIf = "eases:straightening"),
        )
        val plan = followed(1, *items).copy(answers = mapOf("eases" to "bending"))
        assertEquals(listOf("calf", "raise"), build(listOf(plan)).tasks.map { it.exercise.id })
    }

    @Test
    fun coreOnlyHidesOptionalItems() {
        val plan = followed(1, PlanItem("calf", schedule = night()), PlanItem("raise", schedule = night(), core = false))
        val agenda = build(listOf(plan), coreOnly = true)
        assertEquals(listOf("calf"), agenda.tasks.map { it.exercise.id })
        assertEquals(1, agenda.hiddenOptional)
    }

    @Test
    fun soreSideChoiceFlowsIntoTheDose() {
        val item = PlanItem("calf", dose = Dose(sets = 1, holdSec = 30, sides = Sides.AFFECTED), schedule = night())
        val left = build(listOf(followed(1, item).copy(side = SideChoice.LEFT))).tasks.single()
        assertEquals(Side.LEFT, left.affected)
        val both = build(listOf(followed(1, item).copy(side = SideChoice.BOTH))).tasks.single()
        assertEquals(Sides.BOTH, both.dose.sides)
    }

    @Test
    fun planNotStartedYetShowsNothing() {
        val plan = followed(1, PlanItem("calf", schedule = night())).copy(startDay = monday + 1)
        assertTrue(build(listOf(plan)).groups.isEmpty())
    }

    @Test
    fun remindersOnePerSlotOnlyWhenPendingAndDeskOnlyOnWorkdays() {
        val plan = followed(
            1,
            PlanItem("calf", schedule = night()),
            PlanItem("tuck", schedule = Schedule(slots = listOf(SlotId.DESK))),
            PlanItem("raise", schedule = Schedule(slots = listOf(SlotId.ANYTIME))),
        )
        val mondayEvents = Reminders.events(build(listOf(plan)), listOf(plan), prefs, ZoneOffset.UTC)
        assertEquals(listOf(SlotId.DESK, SlotId.NIGHT), mondayEvents.map { it.slot?.slot })

        val sunday = monday + 6
        val sundayEvents = Reminders.events(build(listOf(plan), day = sunday), listOf(plan), prefs, ZoneOffset.UTC)
        assertEquals(listOf(SlotId.NIGHT), sundayEvents.map { it.slot?.slot })

        val allDone = build(listOf(plan), logs = mapOf(TaskRef(1, 0, 0) to TaskStatus.DONE, TaskRef(1, 1, 0) to TaskStatus.SKIPPED))
        assertTrue(Reminders.events(allDone, listOf(plan), prefs, ZoneOffset.UTC).isEmpty())
    }

    @Test
    fun dailyReminderModeGivesOneEventAtItsTime() {
        val plan = followed(1, PlanItem("calf", schedule = night()), PlanItem("tuck", schedule = Schedule(slots = listOf(SlotId.MORNING))))
            .copy(reminderMode = ReminderMode.DAILY, dailyReminderMin = 19 * 60)
        val events = Reminders.events(build(listOf(plan)), listOf(plan), prefs, ZoneOffset.UTC)
        val expected = LocalDate.ofEpochDay(monday).atTime(19, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(listOf(ReminderEvent(expected, monday, enrollmentId = 1)), events)
    }

    @Test
    fun noRemindersWhenOff() {
        val plan = followed(1, PlanItem("calf", schedule = night())).copy(reminderMode = ReminderMode.OFF)
        assertTrue(Reminders.events(build(listOf(plan)), listOf(plan), prefs, ZoneOffset.UTC).isEmpty())
    }

    @Test
    fun slotKeyRoundTrips() {
        val key = SlotKey(SlotId.DESK, 1)
        assertEquals(key, SlotKey.parse(key.id))
        assertNull(SlotKey.parse("nonsense"))
        assertEquals("Desk break 2", key.label())
    }

    private fun night() = Schedule(slots = listOf(SlotId.NIGHT))

    private fun build(
        plans: List<FollowedPlan>,
        day: Long = monday,
        logs: Map<TaskRef, TaskStatus> = emptyMap(),
        runDays: Set<Long> = emptySet(),
        coreOnly: Boolean = false,
    ) = AgendaBuilder.build(day, plans, exercises, logs, runDays, prefs, coreOnly = coreOnly)

    private fun followed(id: Long, vararg items: PlanItem): FollowedPlan {
        val plan = Plan(
            id = "plan-$id",
            name = "Plan $id",
            kind = PlanKind.FOCUSED,
            category = PlanCategory.REHAB,
            goal = "g",
            whoFor = "w",
            estMinutes = 10,
            phases = listOf(Phase(id = "p1", name = "Phase 1", items = items.toList())),
        )
        return FollowedPlan(id = id, plan = plan, phaseId = "p1", items = items.toList(), startDay = monday)
    }

    private fun exercise(id: String, laterality: Laterality, measure: Measure, dose: Dose) = Exercise(
        id = id,
        name = id,
        bodyAreas = listOf(BodyArea.CALF),
        kind = ExerciseKind.STRENGTH,
        position = Position.STANDING,
        laterality = laterality,
        measure = measure,
        defaultDose = dose,
        why = "w",
        setup = listOf("s"),
        cues = listOf("c"),
        feel = "f",
        stopIf = "s",
    )
}
