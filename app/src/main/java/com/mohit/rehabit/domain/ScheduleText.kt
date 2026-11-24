package com.mohit.rehabit.domain

import com.mohit.rehabit.content.DayRule
import com.mohit.rehabit.content.Schedule
import com.mohit.rehabit.content.SlotId
import com.mohit.rehabit.content.Weekday

val SlotId.label: String
    get() = when (this) {
        SlotId.WAKE -> "On waking"
        SlotId.MORNING -> "Morning"
        SlotId.DESK -> "Desk break"
        SlotId.MIDDAY -> "Midday"
        SlotId.EVENING -> "Evening"
        SlotId.NIGHT -> "Night"
        SlotId.ANYTIME -> "Any time"
    }

/** e.g. "Daily · Night", "2× a day · Morning, Night", "Every other day · Night · not on run days". */
fun scheduleText(s: Schedule): String {
    val times = if (s.timesPerDay > 1) "${s.timesPerDay}× a day" else null
    val days = when (s.days) {
        DayRule.DAILY -> if (times == null) "Daily" else null
        DayRule.EVERY_N_DAYS -> when (val n = s.everyNDays ?: 2) {
            2 -> "Every other day"
            else -> "Every $n days"
        }
        DayRule.WEEKDAYS -> s.weekdays.sortedBy { it.ordinal }.joinToString(", ") { it.label }
    }
    val frequency = listOfNotNull(times, days).joinToString(", ")
    val slots = s.slots.distinct().joinToString(", ") { it.label }.ifEmpty { null }
    val runDay = if (s.notOnRunDay) "not on run days" else null
    return listOfNotNull(frequency, slots, runDay).joinToString(" · ")
}

private val Weekday.label: String get() = name.lowercase().replaceFirstChar { it.uppercase() }
