package com.mohit.rehabit.domain

import com.mohit.rehabit.content.Equipment
import com.mohit.rehabit.content.SlotId
import java.time.DayOfWeek

/**
 * One concrete time slot in a day. Slots can repeat within an item's schedule
 * (e.g. DESK twice = desk break 1 and desk break 2), so [index] tells them apart.
 */
data class SlotKey(val slot: SlotId, val index: Int = 0) {
    /** Stable string form for navigation routes and notification extras, e.g. "DESK-1". */
    val id: String get() = "${slot.name}-$index"

    companion object {
        fun parse(id: String): SlotKey? {
            val dash = id.lastIndexOf('-')
            if (dash <= 0) return null
            val slot = runCatching { SlotId.valueOf(id.substring(0, dash)) }.getOrNull() ?: return null
            val index = id.substring(dash + 1).toIntOrNull() ?: return null
            return SlotKey(slot, index)
        }
    }
}

/** The user's day: slot times, workdays and desk breaks. Set in first-run setup, editable in Settings. */
data class DayPrefs(
    val setupDone: Boolean = false,
    /** The WAKE slot uses the phone's next alarm-clock time on days it has one. */
    val wakeFollowsAlarm: Boolean = true,
    val wakeMin: Int = 6 * 60 + 30,
    val morningMin: Int = 7 * 60 + 30,
    val middayMin: Int = 13 * 60,
    val eveningMin: Int = 18 * 60 + 30,
    val nightMin: Int = 21 * 60 + 30,
    val workdays: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    ),
    /** Desk-break reminders on workdays (2 a day). Desk items still show on the checklist when off. */
    val deskBreaks: Boolean = true,
    val desk1Min: Int = 11 * 60,
    val desk2Min: Int = 15 * 60,
    val equipment: Set<Equipment> = emptySet(),
    /** Days the symptom check-in card shows on Today (default Mon, Wed, Fri). */
    val checkInDays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
    /** Suggestions the user said "not now" to, as "key@epochDay" (hidden for 14 days). */
    val dismissedSuggestions: Set<String> = emptySet(),
    /** The day (epoch day) "Core only" is switched on for; resets automatically the next day. */
    val coreOnlyDay: Long = -1,
) {
    /** Minute of day for a slot; [wakeAlarmMin] is the phone alarm's time that day, if any. */
    fun slotMinute(key: SlotKey, wakeAlarmMin: Int? = null): Int = when (key.slot) {
        SlotId.WAKE -> if (wakeFollowsAlarm && wakeAlarmMin != null) wakeAlarmMin else wakeMin
        SlotId.MORNING -> morningMin
        SlotId.DESK -> if (key.index % 2 == 0) desk1Min else desk2Min
        SlotId.MIDDAY -> middayMin
        SlotId.EVENING -> eveningMin
        SlotId.NIGHT -> nightMin
        SlotId.ANYTIME -> ANYTIME_SORT_MIN
    }

    companion object {
        /** "Any time" items sort after everything else and never get a reminder. */
        const val ANYTIME_SORT_MIN = 24 * 60
    }
}

/** e.g. "Desk break 2", "Night", "On waking". */
fun SlotKey.label(): String = when (slot) {
    SlotId.DESK -> "Desk break ${index % 2 + 1}"
    else -> slot.label
}
