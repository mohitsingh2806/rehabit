package com.mohit.rehabit.domain

import com.mohit.rehabit.content.Exercise
import com.mohit.rehabit.content.Phase
import com.mohit.rehabit.content.Plan
import com.mohit.rehabit.content.PlanCategory
import com.mohit.rehabit.content.PlanItem
import com.mohit.rehabit.content.PlanKind
import com.mohit.rehabit.content.Schedule
import com.mohit.rehabit.content.SlotId
import com.mohit.rehabit.content.USER_ID_PREFIX

/** Pure helpers behind the builder: ids, estimates and combining plans. */
object Builder {

    /** "Knee: physio plan!" → "knee-physio-plan" */
    fun slug(text: String): String =
        text.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(30).trim('-')

    /** A new user id like "my-knee-physio-plan-k3f9", never one of [taken]. */
    fun newId(name: String, taken: Set<String>, salt: Long): String {
        val base = USER_ID_PREFIX + slug(name).ifEmpty { "item" }
        var n = salt
        while (true) {
            val id = "$base-${java.lang.Long.toString(Math.floorMod(n, 1_679_616L), 36).padStart(4, '0')}"
            if (id !in taken) return id
            n += 7919
        }
    }

    val defaultSchedule = Schedule(slots = listOf(SlotId.NIGHT))

    /** Minutes for the items as one session, clamped to what the validator accepts (1-180). */
    fun estimateMinutes(items: List<PlanItem>, exercises: Map<String, Exercise>): Int {
        val session = items.mapNotNull { i -> exercises[i.exerciseId]?.let { SessionItem(it, i.dose ?: it.defaultDose) } }
        val ms = SessionScript.estimateMs(SessionScript.build(session))
        return ((ms + 59_999) / 60_000).toInt().coerceIn(1, 180)
    }

    /**
     * Combine several plans/routines (each with the phase to use) into one list. The same exercise
     * at the same time of day is kept once, with the bigger dose. Routines have no schedules; for a
     * daily plan, items that came without a schedule get "daily, at night".
     */
    fun combine(sources: List<Pair<Plan, Phase>>, kind: PlanKind, exercises: Map<String, Exercise>): List<PlanItem> {
        val all = sources.flatMap { (_, phase) -> phase.items }.map { item ->
            when (kind) {
                PlanKind.ON_DEMAND -> item.copy(schedule = null, showIf = null)
                PlanKind.FOCUSED -> item.copy(schedule = item.schedule ?: defaultSchedule, showIf = null)
            }
        }
        fun size(i: PlanItem): Long {
            val e = exercises[i.exerciseId] ?: return 0
            return SessionScript.estimateMs(SessionScript.build(listOf(SessionItem(e, i.dose ?: e.defaultDose))))
        }
        return all.groupBy { it.exerciseId to it.schedule?.slots?.toSet() }
            .values
            .map { same -> same.maxBy(::size).copy(core = same.any { it.core }) }
    }

    /** A user plan/routine with one phase, valid for the content validator. */
    fun userPlan(
        id: String,
        name: String,
        kind: PlanKind,
        goal: String,
        trackedMetric: String?,
        items: List<PlanItem>,
        exercises: Map<String, Exercise>,
    ): Plan {
        val cleanItems = items.map {
            if (kind == PlanKind.ON_DEMAND) it.copy(schedule = null) else it.copy(schedule = it.schedule ?: defaultSchedule)
        }
        return Plan(
            id = id,
            name = name.trim(),
            kind = kind,
            category = PlanCategory.CUSTOM,
            goal = goal.trim().ifEmpty { if (kind == PlanKind.FOCUSED) "Your own daily plan." else "Your own routine." },
            whoFor = "You (made in Rehabit).",
            estMinutes = estimateMinutes(cleanItems, exercises),
            phases = listOf(Phase(id = "main", name = "Exercises", items = cleanItems)),
            trackedMetric = trackedMetric?.trim()?.ifEmpty { null }.takeIf { kind == PlanKind.FOCUSED },
            reviewed = true,
        )
    }
}
