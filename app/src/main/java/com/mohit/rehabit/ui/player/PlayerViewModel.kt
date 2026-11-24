package com.mohit.rehabit.ui.player

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mohit.rehabit.content.ContentBundle
import com.mohit.rehabit.content.ContentRepository
import com.mohit.rehabit.content.ContentState
import com.mohit.rehabit.content.Exercise
import com.mohit.rehabit.content.PlanCategory
import com.mohit.rehabit.data.AgendaRepository
import com.mohit.rehabit.data.Feedback
import com.mohit.rehabit.data.TaskStatus
import com.mohit.rehabit.data.RehabitDatabase
import com.mohit.rehabit.data.SessionEntity
import com.mohit.rehabit.data.SessionExerciseEntity
import com.mohit.rehabit.domain.Cue
import com.mohit.rehabit.domain.CueAction
import com.mohit.rehabit.domain.SessionItem
import com.mohit.rehabit.domain.SessionScript
import com.mohit.rehabit.domain.SlotKey
import com.mohit.rehabit.domain.Task
import com.mohit.rehabit.domain.label
import com.mohit.rehabit.domain.Step
import com.mohit.rehabit.domain.StepKind
import com.mohit.rehabit.player.SessionAudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface PlayerRequest {
    data class PlanPhase(val planId: String, val phaseId: String) : PlayerRequest
    data class SingleExercise(val exerciseId: String) : PlayerRequest

    /** The pending items of one time slot on the daily checklist. */
    data class Slot(val day: Long, val slotId: String) : PlayerRequest
}

data class PlayerState(
    val loading: Boolean = true,
    val error: String? = null,
    val title: String = "",
    val isRehab: Boolean = false,
    val planId: String? = null,
    val phaseId: String? = null,
    val items: List<SessionItem> = emptyList(),
    val steps: List<Step> = emptyList(),
    val index: Int = 0,
    val elapsedMs: Long = 0,
    val paused: Boolean = false,
    val finished: Boolean = false,
    /** Indices of WORK steps the user completed (not skipped). */
    val completed: Set<Int> = emptySet(),
    val voiceOn: Boolean = true,
    val startedAt: Long = 0,
    val endedAt: Long = 0,
    val category: PlanCategory? = null,
    /** For slot sessions: the checklist task behind each item. */
    val slotTasks: List<Task> = emptyList(),
    val slotDay: Long? = null,
) {
    val step: Step? get() = steps.getOrNull(index)

    fun workPlanned(item: Int): Int = steps.count { it.kind == StepKind.WORK && it.itemIndex == item }

    fun workDone(item: Int): Int = completed.count { steps[it].itemIndex == item }

    val exercisesDone: Int get() = items.indices.count { workDone(it) > 0 }

    /** The exercise after the current one, if any. */
    val nextExercise: Exercise? get() = step?.let { items.getOrNull(it.itemIndex + 1)?.exercise }
}

/**
 * Plays a [SessionScript] timeline: a 50 ms ticker advances the current step, fires its cues
 * (voice, beeps, vibration) and moves on when a timed step ends. Open-ended steps wait for Done.
 */
class PlayerViewModel(
    context: Context,
    content: ContentRepository,
    private val db: RehabitDatabase,
    private val agenda: AgendaRepository,
    /** Outlives this screen, so saving isn't cancelled when the player closes. */
    private val appScope: CoroutineScope,
    request: PlayerRequest,
) : ViewModel() {

    private val audio = SessionAudio(context)
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val ready = content.state.filterIsInstance<ContentState.Ready>().first()
            _state.value = load(ready.bundle, request)
            if (_state.value.error == null) {
                _state.value = _state.value.copy(startedAt = System.currentTimeMillis())
                enter(0)
                runTicker()
            }
        }
    }

    private suspend fun load(bundle: ContentBundle, request: PlayerRequest): PlayerState {
        var base = PlayerState(loading = false)
        val (title, items, rehab) = when (request) {
            is PlayerRequest.PlanPhase -> {
                val plan = bundle.plansById[request.planId]
                val phase = plan?.phases?.firstOrNull { it.id == request.phaseId }
                    ?: return base.copy(error = "This plan isn't in the library any more.")
                val items = phase.items.mapNotNull { item ->
                    bundle.exercisesById[item.exerciseId]?.let { SessionItem(it, item.dose ?: it.defaultDose) }
                }
                val title = if (plan.phases.size > 1) "${plan.name} · ${phase.name}" else plan.name
                base = base.copy(category = plan.category)
                Triple(title, items, plan.category == PlanCategory.REHAB)
            }
            is PlayerRequest.Slot -> {
                val key = SlotKey.parse(request.slotId) ?: return base.copy(error = "This session isn't available.")
                val tasks = agenda.agendaFor(request.day).groups.firstOrNull { it.key == key }?.pending.orEmpty()
                if (tasks.isEmpty()) return base.copy(error = "Nothing left to do in this slot. Nice work!")
                base = base.copy(slotTasks = tasks, slotDay = request.day)
                Triple("${key.label()} session", tasks.map { it.item }, tasks.any { it.rehab })
            }
            is PlayerRequest.SingleExercise -> {
                val e = bundle.exercisesById[request.exerciseId]
                    ?: return base.copy(error = "This exercise isn't in the library any more.")
                Triple(e.name, listOf(SessionItem(e, e.defaultDose)), false)
            }
        }
        if (items.isEmpty()) return base.copy(error = "Nothing to do in this session.")
        val (planId, phaseId) = (request as? PlayerRequest.PlanPhase)?.let { it.planId to it.phaseId } ?: (null to null)
        return base.copy(
            title = title,
            isRehab = rehab,
            planId = planId,
            phaseId = phaseId,
            items = items,
            steps = SessionScript.build(items),
        )
    }

    private suspend fun runTicker() {
        var last = SystemClock.elapsedRealtime()
        while (viewModelScope.isActive) {
            delay(50)
            val now = SystemClock.elapsedRealtime()
            tick(now - last)
            last = now
        }
    }

    private fun tick(dt: Long) {
        val s = _state.value
        if (s.paused || s.finished || s.loading) return
        val step = s.step ?: return
        val old = s.elapsedMs
        val new = old + dt
        step.cues.filter { it.atMs in (old + 1)..new }.forEach(::fire)
        _state.value = s.copy(elapsedMs = new)
        val duration = step.durationMs
        if (duration != null && new >= duration) complete()
    }

    private fun fire(cue: Cue) {
        when (val a = cue.action) {
            is CueAction.Speak -> audio.speak(a.text)
            is CueAction.Beep -> audio.beep(a.tone)
            CueAction.Buzz -> audio.buzz()
        }
    }

    private fun enter(index: Int) {
        val s = _state.value
        if (index >= s.steps.size) {
            finish()
            return
        }
        _state.value = s.copy(index = index, elapsedMs = 0, completed = s.completed - index)
        s.steps[index].cues.filter { it.atMs <= 0 }.forEach(::fire)
    }

    /** The current step finished (timer ran out or the user tapped Done). */
    private fun complete() {
        val s = _state.value
        val step = s.step ?: return
        if (step.kind == StepKind.WORK) _state.value = s.copy(completed = s.completed + s.index)
        enter(s.index + 1)
    }

    fun done() = complete()

    /** Move on without counting the current step as done. */
    fun skip() {
        audio.stopSpeaking()
        enter(_state.value.index + 1)
    }

    fun skipExercise() {
        audio.stopSpeaking()
        val s = _state.value
        val current = s.step?.itemIndex ?: return
        val next = s.steps.indexOfFirst { it.itemIndex > current }
        if (next == -1) finish() else enter(next)
    }

    /** Restart the current step, or go to the previous one if it only just started. */
    fun back() {
        audio.stopSpeaking()
        val s = _state.value
        enter(if (s.elapsedMs > 2_000 || s.index == 0) s.index else s.index - 1)
    }

    fun setPaused(paused: Boolean) {
        if (paused) audio.stopSpeaking()
        _state.value = _state.value.copy(paused = paused)
    }

    fun togglePause() = setPaused(!_state.value.paused)

    fun toggleVoice() {
        val on = !_state.value.voiceOn
        audio.voiceOn = on
        if (!on) audio.stopSpeaking()
        _state.value = _state.value.copy(voiceOn = on)
    }

    fun endEarly() = finish()

    private fun finish() {
        val s = _state.value
        if (s.finished) return
        audio.stopSpeaking()
        _state.value = s.copy(finished = true, paused = false, endedAt = System.currentTimeMillis())
        audio.buzz()
        audio.speak("Session complete. Well done.")
    }

    fun save(feedback: Map<Int, Feedback>, painDuring: Int?) {
        val s = _state.value
        val session = SessionEntity(
            startedAt = s.startedAt,
            endedAt = s.endedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
            title = s.title,
            planId = s.planId,
            phaseId = s.phaseId,
            painDuring = painDuring,
        )
        val exercises = s.items.mapIndexed { i, item ->
            SessionExerciseEntity(
                position = i,
                exerciseId = item.exercise.id,
                stepsDone = s.workDone(i),
                stepsPlanned = s.workPlanned(i),
                feedback = feedback[i],
            )
        }
        appScope.launch {
            db.sessionDao().save(session, exercises)
            creditChecklist(s)
        }
    }

    /** Ticks today's checklist with what was done, and marks a run day after a running routine. */
    private suspend fun creditChecklist(s: PlayerState) {
        fun statusOf(i: Int): TaskStatus? {
            val done = s.workDone(i)
            return when {
                done == 0 -> null
                done >= s.workPlanned(i) -> TaskStatus.DONE
                else -> TaskStatus.PARTIAL
            }
        }
        val day = s.slotDay
        if (day != null && s.slotTasks.size == s.items.size) {
            s.slotTasks.forEachIndexed { i, task ->
                statusOf(i)?.let { agenda.setStatus(day, listOf(task), it) }
            }
        } else {
            val results = s.items.indices.mapNotNull { i -> statusOf(i)?.let { s.items[i].exercise.id to it } }.toMap()
            agenda.creditExercises(LocalDate.now().toEpochDay(), results)
        }
        if (s.category == PlanCategory.RUNNING && s.exercisesDone > 0) {
            agenda.setRunDay(LocalDate.now().toEpochDay(), true)
        }
    }

    override fun onCleared() {
        audio.release()
    }
}
