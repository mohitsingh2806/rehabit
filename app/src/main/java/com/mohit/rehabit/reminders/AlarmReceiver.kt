package com.mohit.rehabit.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mohit.rehabit.AppContainer
import com.mohit.rehabit.container
import com.mohit.rehabit.data.TaskStatus
import com.mohit.rehabit.domain.ReminderEvent
import com.mohit.rehabit.domain.SlotKey
import com.mohit.rehabit.domain.Task
import kotlinx.coroutines.launch
import java.time.LocalDate

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val c = context.container
        c.appScope.launch {
            try {
                handle(context, c, intent)
            } finally {
                result.finish()
            }
        }
    }

    private suspend fun handle(context: Context, c: AppContainer, intent: Intent) {
        when (intent.action) {
            ACTION_TICK -> {
                // Show every reminder that came due since the last tick (never older than 15 min,
                // so a phone that was off doesn't get a pile of stale reminders).
                val now = System.currentTimeMillis()
                val from = maxOf(c.settings.lastReminderHandled(), now - 15 * 60_000L)
                val until = now + 60_000L
                val today = LocalDate.now().toEpochDay()
                (today - 1..today)
                    .flatMap { c.agenda.reminderEventsFor(it) }
                    .filter { it.atMillis in (from + 1)..until }
                    .forEach { notify(context, c, it) }
                c.settings.setLastReminderHandled(until)
                c.scheduler.rescheduleAll()
            }
            ACTION_SNOOZED -> eventFrom(intent)?.let { notify(context, c, it) }
            ACTION_SNOOZE -> eventFrom(intent)?.let {
                Notifier.cancel(context, it)
                c.scheduler.scheduleSnooze(it)
            }
            ACTION_DONE -> eventFrom(intent)?.let { event ->
                Notifier.cancel(context, event)
                c.agenda.setStatus(event.day, pendingTasks(c, event), TaskStatus.DONE)
            }
        }
    }

    private suspend fun pendingTasks(c: AppContainer, event: ReminderEvent): List<Task> {
        val agenda = c.agenda.agendaFor(event.day)
        val slot = event.slot
        return if (slot != null) {
            agenda.groups.firstOrNull { it.key == slot }?.pending.orEmpty()
        } else {
            agenda.tasks.filter { t -> t.pending && t.refs.any { it.enrollmentId == event.enrollmentId } }
        }
    }

    private suspend fun notify(context: Context, c: AppContainer, event: ReminderEvent) {
        val tasks = pendingTasks(c, event)
        if (tasks.isEmpty()) return
        val planName = event.enrollmentId?.let { id -> c.agenda.currentPlans().firstOrNull { it.id == id }?.plan?.name }
        Notifier.show(context, event, tasks, planName)
    }

    companion object {
        const val ACTION_TICK = "com.mohit.rehabit.TICK"
        const val ACTION_SNOOZE = "com.mohit.rehabit.SNOOZE"
        const val ACTION_SNOOZED = "com.mohit.rehabit.SNOOZED"
        const val ACTION_DONE = "com.mohit.rehabit.DONE"
        private const val EXTRA_AT = "at"
        private const val EXTRA_DAY = "day"
        private const val EXTRA_SLOT = "slot"
        private const val EXTRA_ENROLLMENT = "enrollment"

        fun eventIntent(context: Context, action: String, event: ReminderEvent): Intent =
            Intent(context, AlarmReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_AT, event.atMillis)
                .putExtra(EXTRA_DAY, event.day)
                .putExtra(EXTRA_SLOT, event.slot?.id)
                .putExtra(EXTRA_ENROLLMENT, event.enrollmentId ?: -1L)

        private fun eventFrom(intent: Intent): ReminderEvent? {
            val day = intent.getLongExtra(EXTRA_DAY, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE } ?: return null
            val slot = intent.getStringExtra(EXTRA_SLOT)?.let { SlotKey.parse(it) }
            val enrollment = intent.getLongExtra(EXTRA_ENROLLMENT, -1L).takeIf { it >= 0 }
            if (slot == null && enrollment == null) return null
            return ReminderEvent(intent.getLongExtra(EXTRA_AT, 0L), day, slot, enrollment)
        }
    }
}
