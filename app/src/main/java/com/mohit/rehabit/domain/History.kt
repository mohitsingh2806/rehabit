package com.mohit.rehabit.domain

import com.mohit.rehabit.data.TaskStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/** How a day looks on the calendar. Only core items count; optional ones never hurt the score. */
enum class DayMark { NOTHING, EXCUSED, ALL_DONE, PARTIAL, MISSED, IN_PROGRESS, FUTURE }

data class DayScore(val day: Long, val planned: Int, val done: Double, val mark: DayMark)

data class WeekAdherence(val weekStart: Long, val planned: Int, val done: Double) {
    /** Null when nothing was planned (or every day was excused). */
    val percent: Int? get() = if (planned == 0) null else (done * 100 / planned).roundToInt()
}

object History {

    /** Done = 1, partly done = ½, skipped / missed = 0. */
    fun weight(status: TaskStatus?): Double = when (status) {
        TaskStatus.DONE -> 1.0
        TaskStatus.PARTIAL -> 0.5
        else -> 0.0
    }

    fun score(agenda: Agenda, excused: Boolean, today: Long): DayScore {
        val core = agenda.tasks.filter { it.core }
        val planned = core.size
        val done = core.sumOf { weight(it.status) }
        val mark = when {
            agenda.day > today -> DayMark.FUTURE
            excused -> DayMark.EXCUSED
            planned == 0 -> DayMark.NOTHING
            done >= planned -> DayMark.ALL_DONE
            agenda.day == today -> DayMark.IN_PROGRESS
            done == 0.0 -> DayMark.MISSED
            else -> DayMark.PARTIAL
        }
        return DayScore(agenda.day, planned, done, mark)
    }

    fun weekStart(day: Long): Long =
        LocalDate.ofEpochDay(day).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()

    /**
     * Weekly adherence for one plan (all its follows, [enrollmentIds]) over finished days: today is
     * left out (still in progress) and so are excused days. Weeks start on Monday, oldest first.
     */
    fun weekly(enrollmentIds: Set<Long>, agendas: List<Agenda>, excused: Set<Long>, today: Long): List<WeekAdherence> =
        agendas
            .filter { it.day < today && it.day !in excused }
            .groupBy { weekStart(it.day) }
            .map { (week, days) ->
                val tasks = days.flatMap { a -> a.tasks.filter { t -> t.core && t.refs.any { it.enrollmentId in enrollmentIds } } }
                WeekAdherence(week, tasks.size, tasks.sumOf { weight(it.status) })
            }
            .sortedBy { it.weekStart }
}

data class CheckInPoint(val day: Long, val value: Int)

enum class InsightKind { WARNING, GOOD }

data class Insight(val kind: InsightKind, val planId: String?, val title: String, val text: String)

/**
 * Pain-monitoring rules (in the spirit of the pain-monitoring model: some discomfort during
 * exercise is OK if it settles by the next day and isn't building week to week). Suggestions only.
 */
object Insights {
    private val DateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
    const val SESSION_PAIN_LIMIT = 6
    const val RISE = 2
    const val HURT_LIMIT = 3

    fun forPlan(
        planId: String,
        planName: String,
        checkIns: List<CheckInPoint>,
        hurtOrPainSkipsThisWeek: Int,
        today: Long,
    ): List<Insight> {
        val out = mutableListOf<Insight>()
        val points = checkIns.sortedBy { it.day }
        val last = points.lastOrNull()

        if (last != null && last.day >= today - 7 && points.size >= 2) {
            val prev = points[points.size - 2]
            val risingThree = points.size >= 3 && points.takeLast(3).zipWithNext().all { (a, b) -> b.value > a.value }
            if (last.value >= prev.value + RISE || risingThree) {
                out += Insight(
                    InsightKind.WARNING, planId,
                    "$planName: symptoms going up",
                    "Your score went from ${prev.value} to ${last.value}. Ease off for 2-3 days: use the easier " +
                        "versions or fewer sets. If it keeps rising, check in with a physio.",
                )
            }
        }

        if (hurtOrPainSkipsThisWeek >= HURT_LIMIT) {
            out += Insight(
                InsightKind.WARNING, planId,
                "$planName: several exercises hurt this week",
                "Try the easier versions for a few days. If the same exercise keeps hurting, swap it or ask a physio.",
            )
        }

        if (last != null && last.day >= today - 14) {
            val first = points.firstOrNull { it.day >= today - 42 }
            if (first != null && first.day < last.day && first.value - last.value >= RISE) {
                val since = LocalDate.ofEpochDay(first.day).format(DateFormat)
                out += Insight(
                    InsightKind.GOOD, planId,
                    "$planName: down from ${first.value} to ${last.value}",
                    "Your score has dropped since $since. Keep going: steady work is what's doing it.",
                )
            }
        }
        return out
    }

    /** Pain during the latest rehab session, if it was too high and recent. */
    fun sessionPain(pain: Int?, sessionDay: Long?, today: Long): Insight? {
        if (pain == null || sessionDay == null || pain < SESSION_PAIN_LIMIT || sessionDay < today - 2) return null
        return Insight(
            InsightKind.WARNING, null,
            "Your last session hurt ($pain/10)",
            "That's more than these plans aim for (up to about 3-5 out of 10). Next time use the easier version " +
                "or fewer sets; pain should settle by the next morning.",
        )
    }
}
