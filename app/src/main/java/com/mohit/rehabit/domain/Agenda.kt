package com.mohit.rehabit.domain

import com.mohit.rehabit.content.DayRule
import com.mohit.rehabit.content.Dose
import com.mohit.rehabit.content.Exercise
import com.mohit.rehabit.content.Plan
import com.mohit.rehabit.content.PlanCategory
import com.mohit.rehabit.content.PlanItem
import com.mohit.rehabit.content.Schedule
import com.mohit.rehabit.content.Sides
import com.mohit.rehabit.content.SlotId
import com.mohit.rehabit.content.Weekday
import com.mohit.rehabit.data.ReminderMode
import com.mohit.rehabit.data.SideChoice
import com.mohit.rehabit.data.TaskStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** A plan the user follows, with its snapshot of items (see EnrollmentEntity). */
data class FollowedPlan(
    val id: Long,
    val plan: Plan,
    val phaseId: String,
    val items: List<PlanItem>,
    val startDay: Long,
    val side: SideChoice? = null,
    /** questionId → optionId */
    val answers: Map<String, String> = emptyMap(),
    val reminderMode: ReminderMode = ReminderMode.SLOTS,
    val dailyReminderMin: Int = 20 * 60,
    val active: Boolean = true,
) {
    val phaseIndex: Int get() = plan.phases.indexOfFirst { it.id == phaseId }

    /** Items for this user's chooser answers. Index = position in [items] (logs refer to it). */
    fun includes(item: PlanItem): Boolean {
        val condition = item.showIf ?: return true
        val (q, o) = condition.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        return answers[q] == o
    }
}

/** Identifies one checklist occurrence in the logs. */
data class TaskRef(val enrollmentId: Long, val itemIndex: Int, val occurrence: Int)

/** One line on the checklist. The same exercise in the same slot from several plans is merged. */
data class Task(
    val exercise: Exercise,
    val dose: Dose,
    /** The side for "sore side" exercises, if the user chose one. */
    val affected: Side?,
    val core: Boolean,
    val slot: SlotKey,
    val refs: List<TaskRef>,
    val planNames: List<String>,
    val rehab: Boolean,
    /** Moved here from yesterday because yesterday was a run day. */
    val moved: Boolean,
    val notes: List<String>,
    val status: TaskStatus?,
) {
    val key: String get() = "${slot.id}/${exercise.id}"
    val pending: Boolean get() = status == null
    val item: SessionItem get() = SessionItem(exercise, dose, affected)
}

data class SlotGroup(val key: SlotKey, val minute: Int, val tasks: List<Task>) {
    val pending: List<Task> get() = tasks.filter { it.pending }
    val done: Int get() = tasks.count { it.status == TaskStatus.DONE || it.status == TaskStatus.PARTIAL }

    fun estimatedMinutes(tasks: List<Task> = this.tasks): Int = minutesFor(tasks)
}

data class Agenda(
    val day: Long,
    val groups: List<SlotGroup>,
    val runDay: Boolean,
    val coreOnly: Boolean,
    /** Optional items hidden by "Core only". */
    val hiddenOptional: Int,
) {
    val tasks: List<Task> get() = groups.flatMap { it.tasks }
    val pendingCount: Int get() = tasks.count { it.pending }

    /** The slot to act on: the earliest one with something left to do. */
    val hero: SlotGroup? get() = groups.firstOrNull { it.pending.isNotEmpty() }
}

fun minutesFor(tasks: List<Task>): Int {
    if (tasks.isEmpty()) return 0
    val ms = SessionScript.estimateMs(SessionScript.build(tasks.map { it.item }))
    return ((ms + 59_999) / 60_000).toInt().coerceAtLeast(1)
}

object AgendaBuilder {

    /** Fixed calendar: EVERY_N_DAYS counts from the plan's start day; misses don't shift it. */
    fun scheduledOn(s: Schedule, day: Long, startDay: Long): Boolean {
        if (day < startDay) return false
        return when (s.days) {
            DayRule.DAILY -> true
            DayRule.EVERY_N_DAYS -> Math.floorMod(day - startDay, (s.everyNDays ?: 2).toLong()) == 0L
            DayRule.WEEKDAYS -> weekday(day) in s.weekdays
        }
    }

    private fun weekday(day: Long): Weekday = Weekday.entries[LocalDate.ofEpochDay(day).dayOfWeek.value - 1]

    fun build(
        day: Long,
        plans: List<FollowedPlan>,
        exercises: Map<String, Exercise>,
        logs: Map<TaskRef, TaskStatus>,
        runDays: Set<Long>,
        prefs: DayPrefs,
        wakeAlarmMin: Int? = null,
        coreOnly: Boolean = false,
    ): Agenda {
        val ranToday = day in runDays
        data class Raw(val plan: FollowedPlan, val ref: TaskRef, val slot: SlotKey, val exercise: Exercise, val item: PlanItem, val moved: Boolean)

        val raws = mutableListOf<Raw>()
        for (plan in plans) {
            if (!plan.active || day < plan.startDay) continue
            plan.items.forEachIndexed { i, item ->
                if (!plan.includes(item)) return@forEachIndexed
                val s = item.schedule ?: return@forEachIndexed
                val exercise = exercises[item.exerciseId] ?: return@forEachIndexed
                val scheduled = scheduledOn(s, day, plan.startDay)
                val logged = (0 until s.timesPerDay).any { TaskRef(plan.id, i, it) in logs }
                val moved = when {
                    !s.notOnRunDay -> if (scheduled) false else null
                    scheduled && (!ranToday || logged) -> false
                    !ranToday && (day - 1) in runDays && scheduledOn(s, day - 1, plan.startDay) -> true
                    else -> null
                } ?: return@forEachIndexed
                val seen = mutableMapOf<SlotId, Int>()
                s.slots.forEachIndexed { k, slot ->
                    val index = seen.getOrDefault(slot, 0)
                    seen[slot] = index + 1
                    raws += Raw(plan, TaskRef(plan.id, i, k), SlotKey(slot, index), exercise, item, moved)
                }
            }
        }

        val merged = raws.groupBy { it.slot to it.exercise.id }.map { (key, group) ->
            val (slot, _) = key
            val first = group.first()
            val doses = group.map { r -> doseFor(r.item, r.exercise, r.plan.side) }
            val dose = doses.maxBy { SessionScript.estimateMs(SessionScript.build(listOf(SessionItem(first.exercise, it)))) }
            val refs = group.map { it.ref }
            Task(
                exercise = first.exercise,
                dose = dose,
                affected = group.firstNotNullOfOrNull { affectedSide(it.plan.side) },
                core = group.any { it.item.core },
                slot = slot,
                refs = refs,
                planNames = group.map { it.plan.plan.name }.distinct(),
                rehab = group.any { it.plan.plan.category == PlanCategory.REHAB },
                moved = group.all { it.moved },
                notes = group.mapNotNull { it.item.note }.distinct(),
                status = bestStatus(refs.mapNotNull { logs[it] }),
            )
        }

        val visible = if (coreOnly) merged.filter { it.core } else merged
        val order = raws.mapIndexed { n, r -> (r.slot to r.exercise.id) to n }.toMap()
        val groups = visible.groupBy { it.slot }.map { (key, tasks) ->
            SlotGroup(key, prefs.slotMinute(key, wakeAlarmMin), tasks.sortedBy { order[it.slot to it.exercise.id] ?: 0 })
        }.sortedWith(compareBy<SlotGroup>({ it.minute }, { it.key.slot.ordinal }, { it.key.index }))

        return Agenda(day, groups, ranToday, coreOnly, hiddenOptional = merged.size - visible.size)
    }

    /** The plan item's dose; "sore side" becomes "both sides" if the user chose Both. */
    fun doseFor(item: PlanItem, exercise: Exercise, side: SideChoice?): Dose {
        val dose = item.dose ?: exercise.defaultDose
        return if (dose.sides == Sides.AFFECTED && side == SideChoice.BOTH) dose.copy(sides = Sides.BOTH) else dose
    }

    private fun affectedSide(side: SideChoice?): Side? = when (side) {
        SideChoice.LEFT -> Side.LEFT
        SideChoice.RIGHT -> Side.RIGHT
        else -> null
    }

    private fun bestStatus(statuses: List<TaskStatus>): TaskStatus? = statuses.minByOrNull {
        when (it) {
            TaskStatus.DONE -> 0
            TaskStatus.PARTIAL -> 1
            TaskStatus.SKIPPED_PAIN -> 2
            TaskStatus.SKIPPED -> 3
        }
    }
}

/** A reminder to show: for a time slot, or a once-a-day reminder for one plan. */
data class ReminderEvent(
    val atMillis: Long,
    val day: Long,
    val slot: SlotKey? = null,
    val enrollmentId: Long? = null,
)

object Reminders {
    /**
     * Reminders for [agenda]'s day: one per slot that has pending items from plans using SLOTS
     * reminders (desk breaks only on workdays and when switched on; never for "Any time"), plus one
     * per plan using a DAILY reminder that has pending items. Never repeats: each fires once.
     */
    fun events(agenda: Agenda, plans: List<FollowedPlan>, prefs: DayPrefs, zone: ZoneId): List<ReminderEvent> {
        val date = LocalDate.ofEpochDay(agenda.day)
        fun at(minute: Int): Long = date.atTime(LocalTime.of(minute / 60 % 24, minute % 60)).atZone(zone).toInstant().toEpochMilli()

        val slotPlans = plans.filter { it.active && it.reminderMode == ReminderMode.SLOTS }.map { it.id }.toSet()
        val workday = date.dayOfWeek in prefs.workdays
        val slotEvents = agenda.groups.mapNotNull { g ->
            when {
                g.key.slot == SlotId.ANYTIME -> null
                g.key.slot == SlotId.DESK && (!prefs.deskBreaks || !workday) -> null
                g.pending.none { t -> t.refs.any { it.enrollmentId in slotPlans } } -> null
                else -> ReminderEvent(at(g.minute), agenda.day, slot = g.key)
            }
        }
        val dailyEvents = plans.filter { it.active && it.reminderMode == ReminderMode.DAILY }.mapNotNull { p ->
            val hasPending = agenda.tasks.any { t -> t.pending && t.refs.any { it.enrollmentId == p.id } }
            if (hasPending) ReminderEvent(at(p.dailyReminderMin), agenda.day, enrollmentId = p.id) else null
        }
        return (slotEvents + dailyEvents).sortedBy { it.atMillis }
    }
}
