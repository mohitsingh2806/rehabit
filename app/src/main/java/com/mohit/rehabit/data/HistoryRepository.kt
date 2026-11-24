package com.mohit.rehabit.data

import com.mohit.rehabit.content.ContentRepository
import com.mohit.rehabit.content.ContentState
import com.mohit.rehabit.content.PlanCategory
import com.mohit.rehabit.domain.Agenda
import com.mohit.rehabit.domain.AgendaBuilder
import com.mohit.rehabit.domain.CheckInPoint
import com.mohit.rehabit.domain.DayMark
import com.mohit.rehabit.domain.DayPrefs
import com.mohit.rehabit.domain.DayScore
import com.mohit.rehabit.domain.FollowedPlan
import com.mohit.rehabit.domain.History
import com.mohit.rehabit.domain.Insight
import com.mohit.rehabit.domain.Insights
import com.mohit.rehabit.domain.TaskRef
import com.mohit.rehabit.domain.WeekAdherence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOn
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class CheckInPrompt(val planId: String, val planName: String, val metric: String)

data class PlanProgress(
    val planId: String,
    val planName: String,
    val metric: String?,
    val following: Boolean,
    /** The last 4 weeks, oldest first (percent null = nothing planned that week). */
    val weeks: List<WeekAdherence>,
    val checkIns: List<CheckInPoint>,
)

data class ProgressState(
    val today: Long,
    val month: YearMonth,
    val days: Map<Long, DayScore>,
    /** Agendas for the month's past days and today, for the day detail sheet. */
    val agendas: Map<Long, Agenda>,
    val plans: List<PlanProgress>,
    val insights: List<Insight>,
    val excused: Map<Long, ExcuseReason>,
    val checkInPrompts: List<CheckInPrompt>,
    val checkInsByDay: Map<Long, Map<String, Int>>,
    val hasPlans: Boolean,
)

private data class Inputs1(
    val ready: ContentState.Ready,
    val enrollments: List<EnrollmentEntity>,
    val logs: List<TaskLogEntity>,
    val runDays: List<Long>,
    val prefs: DayPrefs,
)

private data class Inputs2(
    val checkIns: List<CheckInEntity>,
    val excused: List<ExcusedDayEntity>,
    val sessions: List<SessionEntity>,
    val hurt: List<HurtRow>,
)

/** Calendar, adherence, check-ins and pain-rule insights, rebuilt from the stored ticks. */
class HistoryRepository(
    private val content: ContentRepository,
    private val db: RehabitDatabase,
    private val settings: SettingsRepository,
    private val agenda: AgendaRepository,
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun millis(day: Long): Long = LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun dayOf(millis: Long): Long = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

    fun progressFlow(month: YearMonth): Flow<ProgressState> {
        val today = LocalDate.now().toEpochDay()
        val from = minOf(month.atDay(1).toEpochDay(), History.weekStart(today) - 21)
        val to = maxOf(month.atEndOfMonth().toEpochDay(), today)

        val a = combine(
            content.state.filterIsInstance<ContentState.Ready>(),
            db.enrollmentDao().observeAll(),
            db.taskLogDao().observeRange(from, to),
            db.runDayDao().observeRange(from - 1, to),
            settings.flow,
        ) { ready, enrollments, logs, runDays, prefs -> Inputs1(ready, enrollments, logs, runDays, prefs) }
        val b = combine(
            db.checkInDao().observeAll(),
            db.excusedDayDao().observeRange(from, to),
            db.sessionDao().observeSince(millis(today - 42)),
            db.sessionDao().observeHurtSince(millis(today - 6)),
        ) { checkIns, excused, sessions, hurt -> Inputs2(checkIns, excused, sessions, hurt) }

        return combine(a, b) { i1, i2 -> build(month, today, from, i1, i2) }.flowOn(Dispatchers.Default)
    }

    private fun build(month: YearMonth, today: Long, from: Long, i1: Inputs1, i2: Inputs2): ProgressState {
        val bundle = i1.ready.bundle
        val plans: List<FollowedPlan> = agenda.followed(i1.enrollments, bundle)
        val logsByDay = i1.logs.groupBy { it.epochDay }
        val runDays = i1.runDays.toSet()
        val excused = i2.excused.associate { it.epochDay to it.reason }

        val agendas = (from..today).associateWith { day ->
            AgendaBuilder.build(
                day = day,
                plans = plans,
                exercises = bundle.exercisesById,
                logs = logsByDay[day].orEmpty().associate { TaskRef(it.enrollmentId, it.itemIndex, it.occurrence) to it.status },
                runDays = runDays,
                prefs = i1.prefs,
            )
        }

        val monthDays = (month.atDay(1).toEpochDay()..month.atEndOfMonth().toEpochDay())
        val days = monthDays.associateWith { day ->
            val a = agendas[day]
            if (a == null) DayScore(day, 0, 0.0, if (day > today) DayMark.FUTURE else DayMark.NOTHING)
            else History.score(a, day in excused, today)
        }

        val checkInsByPlan = i2.checkIns.groupBy { it.planId }
        val weekStarts = (3 downTo 0).map { History.weekStart(today) - it * 7L }
        val agendaList = agendas.values.toList()

        // One row per plan that's followed now or was during this window.
        val byPlan = plans.filter { it.endDay == null || it.endDay > from }.groupBy { it.plan.id }
        val planProgress = byPlan.map { (planId, follows) ->
            val plan = follows.first().plan
            val ids = follows.map { it.id }.toSet()
            val weeks = History.weekly(ids, agendaList, excused.keys, today).associateBy { it.weekStart }
            PlanProgress(
                planId = planId,
                planName = plan.name,
                metric = plan.trackedMetric,
                following = follows.any { it.endDay == null },
                weeks = weekStarts.map { weeks[it] ?: WeekAdherence(it, 0, 0.0) },
                checkIns = checkInsByPlan[planId].orEmpty().map { CheckInPoint(it.epochDay, it.value) },
            )
        }.sortedBy { it.planName }

        // Pain-rule insights.
        val weekAgo = today - 6
        val insights = mutableListOf<Insight>()
        for ((planId, follows) in byPlan) {
            val plan = follows.first().plan
            if (plan.category != PlanCategory.REHAB) continue
            val ids = follows.map { it.id }.toSet()
            val painSkips = i1.logs.count { it.epochDay >= weekAgo && it.enrollmentId in ids && it.status == TaskStatus.SKIPPED_PAIN }
            val hurt = i2.hurt.count { it.planId == planId }
            insights += Insights.forPlan(
                planId, plan.name,
                checkInsByPlan[planId].orEmpty().map { CheckInPoint(it.epochDay, it.value) },
                painSkips + hurt,
                today,
            )
        }
        i2.sessions.lastOrNull { it.painDuring != null }?.let { s ->
            Insights.sessionPain(s.painDuring, dayOf(s.startedAt), today)?.let { insights.add(0, it) }
        }

        // Check-in card on Today.
        val checkInToday = i2.checkIns.filter { it.epochDay == today }.map { it.planId }.toSet()
        val isCheckInDay = LocalDate.ofEpochDay(today).dayOfWeek in i1.prefs.checkInDays
        val prompts = if (!isCheckInDay) emptyList() else plans
            .filter { it.endDay == null && it.active && it.startDay <= today && it.plan.trackedMetric != null }
            .distinctBy { it.plan.id }
            .filter { it.plan.id !in checkInToday }
            .map { CheckInPrompt(it.plan.id, it.plan.name, it.plan.trackedMetric.orEmpty()) }

        return ProgressState(
            today = today,
            month = month,
            days = days,
            agendas = agendas.filterKeys { it in monthDays },
            plans = planProgress,
            insights = insights,
            excused = excused,
            checkInPrompts = prompts,
            checkInsByDay = i2.checkIns.groupBy { it.epochDay }.mapValues { (_, list) -> list.associate { it.planId to it.value } },
            hasPlans = plans.isNotEmpty(),
        )
    }

    suspend fun saveCheckIns(day: Long, values: Map<String, Int>) {
        val now = System.currentTimeMillis()
        db.checkInDao().upsertAll(values.map { (planId, v) -> CheckInEntity(epochDay = day, planId = planId, value = v.coerceIn(0, 10), timestamp = now) })
    }

    suspend fun setExcused(day: Long, reason: ExcuseReason?) {
        if (reason == null) db.excusedDayDao().delete(day) else db.excusedDayDao().upsert(ExcusedDayEntity(day, reason))
    }
}
