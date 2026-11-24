package com.mohit.rehabit.data

import android.app.AlarmManager
import android.content.Context
import com.mohit.rehabit.content.ContentBundle
import com.mohit.rehabit.content.ContentLoader
import com.mohit.rehabit.content.ContentRepository
import com.mohit.rehabit.content.ContentState
import com.mohit.rehabit.content.PlanItem
import com.mohit.rehabit.domain.Agenda
import com.mohit.rehabit.domain.AgendaBuilder
import com.mohit.rehabit.domain.DayPrefs
import com.mohit.rehabit.domain.FollowedPlan
import com.mohit.rehabit.domain.ReminderEvent
import com.mohit.rehabit.domain.Reminders
import com.mohit.rehabit.domain.Task
import com.mohit.rehabit.domain.TaskRef
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Everything the Today screen needs. */
data class TodayState(
    val agenda: Agenda,
    /** Followed plans (active and paused). */
    val plans: List<FollowedPlan>,
    val prefs: DayPrefs,
    val nowMinute: Int,
)

/**
 * The daily checklist: builds today's agenda from followed plans, ticks and run days, and records
 * changes. Every change re-arms reminders via [onChanged].
 */
class AgendaRepository(
    context: Context,
    private val content: ContentRepository,
    private val db: RehabitDatabase,
    private val settings: SettingsRepository,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val itemsSerializer = ListSerializer(PlanItem.serializer())

    /** Set by the container: re-arms reminders after any change. */
    var onChanged: suspend () -> Unit = {}

    suspend fun bundle(): ContentBundle = content.state.filterIsInstance<ContentState.Ready>().first().bundle

    fun encodeItems(items: List<PlanItem>): String = ContentLoader.lenientJson.encodeToString(itemsSerializer, items)

    private fun decodeItems(json: String): List<PlanItem> =
        runCatching { ContentLoader.lenientJson.decodeFromString(itemsSerializer, json) }.getOrDefault(emptyList())

    /** Plans the user follows; a plan removed from the content is skipped (its logs stay). */
    fun followed(entities: List<EnrollmentEntity>, bundle: ContentBundle): List<FollowedPlan> = entities.mapNotNull { e ->
        val plan = bundle.plansById[e.planId] ?: return@mapNotNull null
        FollowedPlan(
            id = e.id,
            plan = plan,
            phaseId = e.phaseId,
            items = decodeItems(e.itemsJson),
            startDay = e.startEpochDay,
            side = e.side,
            answers = parseAnswers(e.answers),
            reminderMode = e.reminderMode,
            dailyReminderMin = e.dailyReminderMin,
            active = e.status != FollowStatus.PAUSED,
            endDay = e.endedEpochDay,
        )
    }

    /** The phone's next alarm-clock time as a minute of day, if it rings on [day]. */
    fun wakeAlarmMinute(day: Long): Int? {
        val info = runCatching { alarmManager.nextAlarmClock }.getOrNull() ?: return null
        val at = Instant.ofEpochMilli(info.triggerTime).atZone(ZoneId.systemDefault())
        return if (at.toLocalDate().toEpochDay() == day) at.hour * 60 + at.minute else null
    }

    private fun build(
        day: Long,
        plans: List<FollowedPlan>,
        bundle: ContentBundle,
        logs: List<TaskLogEntity>,
        runDays: List<Long>,
        prefs: DayPrefs,
    ): Agenda = AgendaBuilder.build(
        day = day,
        plans = plans,
        exercises = bundle.exercisesById,
        logs = logs.associate { TaskRef(it.enrollmentId, it.itemIndex, it.occurrence) to it.status },
        runDays = runDays.toSet(),
        prefs = prefs,
        wakeAlarmMin = wakeAlarmMinute(day),
        coreOnly = prefs.coreOnlyDay == day,
    )

    suspend fun agendaFor(day: Long): Agenda {
        val bundle = bundle()
        val plans = followed(db.enrollmentDao().getCurrent(), bundle)
        return build(day, plans, bundle, db.taskLogDao().getDay(day), db.runDayDao().getRange(day - 1, day), settings.get())
    }

    suspend fun currentPlans(): List<FollowedPlan> = followed(db.enrollmentDao().getCurrent(), bundle())

    suspend fun reminderEventsFor(day: Long): List<ReminderEvent> {
        val bundle = bundle()
        val plans = followed(db.enrollmentDao().getCurrent(), bundle)
        if (plans.none { it.active && it.reminderMode != ReminderMode.OFF }) return emptyList()
        val prefs = settings.get()
        val agenda = build(day, plans, bundle, db.taskLogDao().getDay(day), db.runDayDao().getRange(day - 1, day), prefs)
        return Reminders.events(agenda, plans, prefs, ZoneId.systemDefault())
    }

    private fun clock(): Flow<Pair<Long, Int>> = flow {
        while (true) {
            val now = LocalTime.now()
            emit(LocalDate.now().toEpochDay() to now.hour * 60 + now.minute)
            delay(20_000)
        }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun todayFlow(): Flow<TodayState> = clock().flatMapLatest { (day, nowMinute) ->
        combine(
            content.state.filterIsInstance<ContentState.Ready>(),
            db.enrollmentDao().observeCurrent(),
            db.taskLogDao().observeDay(day),
            db.runDayDao().observeRange(day - 1, day),
            settings.flow,
        ) { ready, entities, logs, runDays, prefs ->
            val plans = followed(entities, ready.bundle)
            TodayState(build(day, plans, ready.bundle, logs, runDays, prefs), plans, prefs, nowMinute)
        }
    }

    fun currentEnrollments(): Flow<List<EnrollmentEntity>> = db.enrollmentDao().observeCurrent()

    // ---- Changes ----

    suspend fun setStatus(day: Long, tasks: List<Task>, status: TaskStatus?) {
        val dao = db.taskLogDao()
        val refs = tasks.flatMap { it.refs }
        if (status == null) {
            refs.forEach { dao.delete(day, it.enrollmentId, it.itemIndex, it.occurrence) }
        } else {
            val now = System.currentTimeMillis()
            dao.upsertAll(refs.map { TaskLogEntity(epochDay = day, enrollmentId = it.enrollmentId, itemIndex = it.itemIndex, occurrence = it.occurrence, status = status, timestamp = now) })
        }
        onChanged()
    }

    /** Ticks today's checklist after a session: per exercise, its earliest pending task. */
    suspend fun creditExercises(day: Long, results: Map<String, TaskStatus>) {
        if (results.isEmpty()) return
        val agenda = agendaFor(day)
        val now = System.currentTimeMillis()
        val logs = results.mapNotNull { (exerciseId, status) ->
            agenda.tasks.firstOrNull { it.pending && it.exercise.id == exerciseId }?.let { it to status }
        }.flatMap { (task, status) ->
            task.refs.map { TaskLogEntity(epochDay = day, enrollmentId = it.enrollmentId, itemIndex = it.itemIndex, occurrence = it.occurrence, status = status, timestamp = now) }
        }
        if (logs.isNotEmpty()) {
            db.taskLogDao().upsertAll(logs)
            onChanged()
        }
    }

    suspend fun setRunDay(day: Long, ran: Boolean) {
        if (ran) db.runDayDao().insert(RunDayEntity(day)) else db.runDayDao().delete(day)
        onChanged()
    }

    suspend fun setCoreOnly(day: Long, on: Boolean) {
        settings.setCoreOnlyDay(if (on) day else -1)
        onChanged()
    }

    suspend fun follow(
        planId: String,
        phaseId: String,
        startDay: Long,
        side: SideChoice?,
        answers: Map<String, String>,
        reminderMode: ReminderMode,
        dailyReminderMin: Int,
    ): Long {
        val plan = bundle().plansById[planId] ?: return -1
        val phase = plan.phases.firstOrNull { it.id == phaseId } ?: plan.phases.first()
        val id = db.enrollmentDao().insert(
            EnrollmentEntity(
                planId = planId,
                phaseId = phase.id,
                itemsJson = encodeItems(phase.items),
                startEpochDay = startDay,
                side = side,
                answers = answers.entries.joinToString(";") { "${it.key}:${it.value}" },
                reminderMode = reminderMode,
                dailyReminderMin = dailyReminderMin,
                createdAt = System.currentTimeMillis(),
            ),
        )
        onChanged()
        return id
    }

    /**
     * Switch phase, or take the latest version of the current phase from the content. Ticks point
     * at item positions in the old list, so history stays correct by ending the current follow and
     * starting a fresh one today with the same settings.
     */
    suspend fun setPhase(enrollmentId: Long, phaseId: String) {
        val dao = db.enrollmentDao()
        val e = dao.get(enrollmentId) ?: return
        val phase = bundle().plansById[e.planId]?.phases?.firstOrNull { it.id == phaseId } ?: return
        val today = LocalDate.now().toEpochDay()
        dao.update(e.copy(status = FollowStatus.ENDED, endedEpochDay = today))
        dao.insert(
            e.copy(
                id = 0,
                phaseId = phase.id,
                itemsJson = encodeItems(phase.items),
                startEpochDay = today,
                endedEpochDay = null,
                createdAt = System.currentTimeMillis(),
            ),
        )
        onChanged()
    }

    /**
     * Changes one item of a followed plan from today on (swap the exercise or change its dose).
     * Like a phase change it ends the current follow and starts a copy today, so history keeps the
     * old item; today's ticks are carried over (same item positions).
     */
    suspend fun replaceItem(enrollmentId: Long, itemIndex: Int, change: (PlanItem) -> PlanItem) {
        val dao = db.enrollmentDao()
        val e = dao.get(enrollmentId) ?: return
        val items = decodeItems(e.itemsJson).toMutableList()
        if (itemIndex !in items.indices) return
        items[itemIndex] = change(items[itemIndex])
        val today = LocalDate.now().toEpochDay()
        dao.update(e.copy(status = FollowStatus.ENDED, endedEpochDay = today))
        val newId = dao.insert(
            e.copy(id = 0, itemsJson = encodeItems(items), startEpochDay = today, endedEpochDay = null, createdAt = System.currentTimeMillis()),
        )
        val carried = db.taskLogDao().getDayFor(today, enrollmentId).map { it.copy(id = 0, enrollmentId = newId) }
        if (carried.isNotEmpty()) db.taskLogDao().upsertAll(carried)
        onChanged()
    }

    suspend fun updateEnrollment(enrollmentId: Long, change: (EnrollmentEntity) -> EnrollmentEntity) {
        val e = db.enrollmentDao().get(enrollmentId) ?: return
        db.enrollmentDao().update(change(e))
        onChanged()
    }

    /**
     * Pause ends the current follow today and keeps a paused copy; resume restarts that copy from
     * today. Paused days are then simply not covered by any follow, so history stays accurate.
     */
    suspend fun pause(enrollmentId: Long) {
        val dao = db.enrollmentDao()
        val e = dao.get(enrollmentId) ?: return
        val today = LocalDate.now().toEpochDay()
        dao.update(e.copy(status = FollowStatus.ENDED, endedEpochDay = today))
        dao.insert(e.copy(id = 0, status = FollowStatus.PAUSED, endedEpochDay = null, createdAt = System.currentTimeMillis()))
        onChanged()
    }

    suspend fun resume(enrollmentId: Long) = updateEnrollment(enrollmentId) {
        it.copy(status = FollowStatus.ACTIVE, startEpochDay = LocalDate.now().toEpochDay())
    }

    suspend fun stop(enrollmentId: Long) = updateEnrollment(enrollmentId) {
        it.copy(status = FollowStatus.ENDED, endedEpochDay = LocalDate.now().toEpochDay())
    }

    /** True if the bundled phase differs from the snapshot the user follows. */
    fun isOutdated(e: EnrollmentEntity, bundle: ContentBundle): Boolean {
        val phase = bundle.plansById[e.planId]?.phases?.firstOrNull { it.id == e.phaseId } ?: return false
        return decodeItems(e.itemsJson) != phase.items
    }

    companion object {
        fun parseAnswers(s: String): Map<String, String> = s.split(';').mapNotNull { pair ->
            val parts = pair.split(':', limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank()) parts[0] to parts[1] else null
        }.toMap()
    }
}
