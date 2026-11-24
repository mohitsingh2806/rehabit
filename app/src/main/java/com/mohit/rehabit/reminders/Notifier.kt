package com.mohit.rehabit.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mohit.rehabit.MainActivity
import com.mohit.rehabit.R
import com.mohit.rehabit.content.SlotId
import com.mohit.rehabit.domain.Agenda
import com.mohit.rehabit.domain.FollowedPlan
import com.mohit.rehabit.domain.ReminderEvent
import com.mohit.rehabit.domain.SlotKey
import com.mohit.rehabit.domain.Task
import com.mohit.rehabit.domain.doseText
import com.mohit.rehabit.domain.label
import com.mohit.rehabit.domain.minutesFor

object Notifier {
    private const val CHANNEL_REMINDERS = "reminders"
    private const val CHANNEL_DESK = "desk"
    const val EXTRA_ROUTE = "route"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Exercise reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "One reminder per time slot or per day for the plans you follow"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DESK, "Desk breaks", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Quiet desk-break reminders on workdays"
            },
        )
    }

    fun notificationId(event: ReminderEvent): Int {
        val slot = event.slot
        return if (slot != null) slotId(slot) else 4000 + ((event.enrollmentId ?: 0L) % 1000).toInt()
    }

    private fun slotId(key: SlotKey) = 3000 + key.slot.ordinal * 10 + key.index

    fun show(context: Context, event: ReminderEvent, tasks: List<Task>, planName: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val slot = event.slot
        val count = tasks.size
        val exercises = if (count == 1) "1 exercise" else "$count exercises"
        val title = when {
            slot != null -> "${slot.label()} · $exercises · ${minutesFor(tasks)} min"
            else -> "${planName ?: "Your plan"} · $exercises left today"
        }
        val lines = tasks.map { "${it.exercise.name} — ${doseText(it.dose, it.exercise.measure, it.exercise.laterality)}" }
        // A single simple item can be done straight from the notification: show its cues.
        val text = if (count == 1) {
            lines.first() + ". " + tasks.first().exercise.cues.joinToString(". ") + "."
        } else {
            tasks.joinToString(", ") { it.exercise.name }
        }
        val style = if (count == 1) {
            NotificationCompat.BigTextStyle().bigText(text)
        } else {
            NotificationCompat.InboxStyle().also { s -> lines.forEach { s.addLine("• $it") } }
        }

        val id = notificationId(event)
        val startRoute = if (slot != null) "player/slot/${event.day}/${slot.id}" else "today"
        val builder = NotificationCompat.Builder(context, if (slot?.slot == SlotId.DESK) CHANNEL_DESK else CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_rehabit)
            .setColor(0xFF2E7D32.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp(context, id, "today"))
            .setAutoCancel(true)
            .addAction(0, "Start", openApp(context, id + 50_000, startRoute))
            .addAction(0, "Done", broadcast(context, AlarmReceiver.ACTION_DONE, event, id + 60_000))
            .addAction(0, "Snooze ${ReminderScheduler.SNOOZE_MIN} min", broadcast(context, AlarmReceiver.ACTION_SNOOZE, event, id + 70_000))
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    fun cancel(context: Context, event: ReminderEvent) {
        NotificationManagerCompat.from(context).cancel(notificationId(event))
    }

    /** Removes reminders whose items are all done (ticked in the app or elsewhere). */
    fun cancelStale(context: Context, agenda: Agenda, plans: List<FollowedPlan>) {
        val nm = NotificationManagerCompat.from(context)
        val pendingSlots = agenda.groups.filter { it.pending.isNotEmpty() }.map { slotId(it.key) }.toSet()
        for (slot in SlotId.entries) for (index in 0..1) {
            val id = slotId(SlotKey(slot, index))
            if (id !in pendingSlots) nm.cancel(id)
        }
        for (p in plans) {
            val pending = agenda.tasks.any { t -> t.pending && t.refs.any { it.enrollmentId == p.id } }
            if (!pending) nm.cancel(notificationId(ReminderEvent(0, agenda.day, enrollmentId = p.id)))
        }
    }

    private fun openApp(context: Context, requestCode: Int, route: String): PendingIntent = PendingIntent.getActivity(
        context, requestCode,
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun broadcast(context: Context, action: String, event: ReminderEvent, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            AlarmReceiver.eventIntent(context, action, event),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
