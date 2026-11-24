package com.mohit.rehabit.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mohit.rehabit.data.AgendaRepository
import com.mohit.rehabit.domain.ReminderEvent
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * One alarm at a time: the next reminder (or a nightly refresh at 03:00, so the wake slot follows
 * alarm-clock changes). When it fires, [AlarmReceiver] shows everything due and calls this again.
 * Re-armed after every change, on app start, reboot, app update and clock/time-zone changes.
 */
class ReminderScheduler(private val context: Context, private val agenda: AgendaRepository) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    suspend fun rescheduleAll() {
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toEpochDay()
        val next = (0L..2L)
            .flatMap { agenda.reminderEventsFor(today + it) }
            .map { it.atMillis }
            .filter { it > now }
            .plus(nextRefresh())
            .min()
        setAlarm(next, tickIntent())
        Notifier.cancelStale(context, agenda.agendaFor(today), agenda.currentPlans())
    }

    fun scheduleSnooze(event: ReminderEvent, minutes: Int = SNOOZE_MIN) {
        setAlarm(System.currentTimeMillis() + minutes * 60_000L, snoozeIntent(event))
    }

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun nextRefresh(): Long {
        val zone = ZoneId.systemDefault()
        var at = LocalDate.now().atTime(LocalTime.of(3, 0)).atZone(zone)
        if (!at.toInstant().isAfter(java.time.Instant.now())) at = at.plusDays(1)
        return at.toInstant().toEpochMilli()
    }

    private fun tickIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, REQ_TICK,
        Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_TICK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun snoozeIntent(event: ReminderEvent): PendingIntent = PendingIntent.getBroadcast(
        context, REQ_SNOOZE_BASE + Notifier.notificationId(event),
        AlarmReceiver.eventIntent(context, AlarmReceiver.ACTION_SNOOZED, event),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun setAlarm(atMillis: Long, pi: PendingIntent) {
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    companion object {
        const val SNOOZE_MIN = 30
        private const val REQ_TICK = 1
        private const val REQ_SNOOZE_BASE = 10_000
    }
}
