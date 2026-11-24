package com.mohit.rehabit.domain

import com.mohit.rehabit.content.BodyArea
import com.mohit.rehabit.content.ContentBundle
import com.mohit.rehabit.content.ContentValidator
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
import com.mohit.rehabit.content.SlotId
import com.mohit.rehabit.content.isUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuilderTest {

    private val calf = Exercise(
        id = "calf", name = "Calf", bodyAreas = listOf(BodyArea.CALF), kind = ExerciseKind.STRETCH_STATIC,
        position = Position.STANDING, laterality = Laterality.EACH_SIDE, measure = Measure.HOLD,
        defaultDose = Dose(sets = 2, holdSec = 30), why = "w", setup = listOf("s"), cues = listOf("c"), feel = "f", stopIf = "s",
    )
    private val bridge = calf.copy(id = "bridge", name = "Bridge", laterality = Laterality.BILATERAL, measure = Measure.REPS, defaultDose = Dose(reps = 10))
    private val exercises = listOf(calf, bridge).associateBy { it.id }

    @Test
    fun slugsAndIds() {
        assertEquals("knee-physio-plan", Builder.slug("Knee: physio plan!"))
        val id = Builder.newId("Knee plan", emptySet(), 42)
        assertTrue(id.startsWith("my-knee-plan-"))
        assertNotEquals(id, Builder.newId("Knee plan", setOf(id), 42))
        assertTrue(Builder.newId("!!!", emptySet(), 1).startsWith("my-item-"))
    }

    @Test
    fun combineKeepsOneOfEachExerciseAtTheSameTimeWithTheBiggerDose() {
        val night = Schedule(slots = listOf(SlotId.NIGHT))
        val a = plan("a", PlanItem("calf", dose = Dose(sets = 1, holdSec = 30), schedule = night), PlanItem("bridge", schedule = night))
        val b = plan("b", PlanItem("calf", dose = Dose(sets = 3, holdSec = 30), schedule = night))
        val merged = Builder.combine(listOf(a to a.phases[0], b to b.phases[0]), PlanKind.FOCUSED, exercises)
        assertEquals(listOf("calf", "bridge"), merged.map { it.exerciseId })
        assertEquals(3, merged.first().dose?.sets)
    }

    @Test
    fun combineIntoRoutineDropsSchedulesAndMergesAcrossTimes() {
        val a = plan("a", PlanItem("calf", schedule = Schedule(slots = listOf(SlotId.MORNING))))
        val b = plan("b", PlanItem("calf", schedule = Schedule(slots = listOf(SlotId.NIGHT))))
        val merged = Builder.combine(listOf(a to a.phases[0], b to b.phases[0]), PlanKind.ON_DEMAND, exercises)
        assertEquals(1, merged.size)
        assertNull(merged.single().schedule)
    }

    @Test
    fun userPlansPassTheContentValidator() {
        val items = listOf(PlanItem("calf"), PlanItem("bridge", core = false))
        val daily = Builder.userPlan("my-x-0001", "My plan", PlanKind.FOCUSED, "", "Knee pain (0-10)", items, exercises)
        val routine = Builder.userPlan("my-y-0001", "My routine", PlanKind.ON_DEMAND, "", "ignored", items, exercises)
        assertTrue(daily.isUser)
        assertEquals(PlanCategory.CUSTOM, daily.category)
        assertNull(routine.trackedMetric)
        val problems = ContentValidator.validate(ContentBundle(1, listOf(calf, bridge), listOf(daily, routine)))
        assertEquals(emptyList<String>(), problems)
    }

    private fun plan(id: String, vararg items: PlanItem) = Plan(
        id = id, name = id, kind = PlanKind.FOCUSED, category = PlanCategory.MOBILITY, goal = "g", whoFor = "w",
        estMinutes = 5, phases = listOf(Phase(id = "p", name = "P", items = items.toList())),
    )
}
