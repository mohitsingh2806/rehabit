package com.mohit.rehabit.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mohit.rehabit.domain.CheckInPoint
import com.mohit.rehabit.domain.DayMark
import com.mohit.rehabit.domain.DayScore
import com.mohit.rehabit.domain.WeekAdherence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.abs

/** Fixed status colours (never themed); always paired with a symbol and the legend. */
private object StatusColors {
    val good = Color(0xFF0CA30C)
    val warning = Color(0xFFFAB219)
    val critical = Color(0xFFD03B3B)
    val onWarning = Color(0xFF0B0B0B)
}

private val ShortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val LongDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

private data class MarkStyle(val fill: Color?, val text: Color?, val symbol: String, val label: String)

@Composable
private fun styleFor(mark: DayMark): MarkStyle = when (mark) {
    DayMark.ALL_DONE -> MarkStyle(StatusColors.good, Color.White, "✓", "All done")
    DayMark.PARTIAL -> MarkStyle(StatusColors.warning, StatusColors.onWarning, "◐", "Partly")
    DayMark.MISSED -> MarkStyle(StatusColors.critical, Color.White, "✕", "Missed")
    DayMark.EXCUSED -> MarkStyle(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "E", "Excused")
    DayMark.IN_PROGRESS -> MarkStyle(null, null, "•", "Today")
    DayMark.NOTHING, DayMark.FUTURE -> MarkStyle(null, null, "", "")
}

/** Month calendar: each day's colour + symbol says how it went. Tap a past day for details. */
@Composable
fun MonthCalendar(
    month: YearMonth,
    days: Map<Long, DayScore>,
    today: Long,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDay: (Long) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month") }
            Text(
                month.month.getDisplayName(JavaTextStyle.FULL, Locale.getDefault()) + " " + month.year,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNext, enabled = month < YearMonth.now()) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
        }
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach {
                Text(
                    it.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val first = month.atDay(1)
        val leading = first.dayOfWeek.value - 1
        val cells: List<LocalDate?> = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { i ->
                    val date = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (date != null) DayCell(date, days[date.toEpochDay()], date.toEpochDay() == today, onDay)
                    }
                }
            }
        }
        // Legend: identity never relies on colour alone.
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            listOf(DayMark.ALL_DONE, DayMark.PARTIAL, DayMark.MISSED, DayMark.EXCUSED).forEach { m ->
                val st = styleFor(m)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(16.dp).clip(CircleShape).background(st.fill ?: Color.Transparent), contentAlignment = Alignment.Center) {
                        Text(st.symbol, style = MaterialTheme.typography.labelSmall, color = st.text ?: Color.Unspecified)
                    }
                    Text(" " + st.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, score: DayScore?, isToday: Boolean, onDay: (Long) -> Unit) {
    val mark = score?.mark ?: DayMark.NOTHING
    val st = styleFor(mark)
    val clickable = mark != DayMark.FUTURE
    var m = Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape)
    if (st.fill != null) m = m.background(st.fill)
    if (isToday) m = m.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
    if (clickable) m = m.clickable { onDay(date.toEpochDay()) }
    Box(m, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = st.text ?: if (mark == DayMark.FUTURE) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            )
            if (st.symbol.isNotEmpty()) {
                Text(st.symbol, style = MaterialTheme.typography.labelSmall, color = st.text ?: MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * Symptom score over time (single series: the title names it, so no legend). 0-10 fixed axis,
 * hairline grid, 2dp line, 8dp markers with a surface ring, the latest value labelled. Tap for values.
 */
@Composable
fun TrendChart(points: List<CheckInPoint>, modifier: Modifier = Modifier) {
    val sorted = remember(points) { points.sortedBy { it.day } }
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val dark = isSystemInDarkTheme()
    val grid = if (dark) Color(0xFF2C2C2A) else Color(0xFFE1E0D9)
    val line = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = muted)
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = ink, fontWeight = FontWeight.Bold)

    Column(modifier) {
        val sel = selected?.let { sorted.getOrNull(it) }
        Text(
            if (sel != null) "${LocalDate.ofEpochDay(sel.day).format(LongDate)}: ${sel.value}/10" else "Tap a point to see its value",
            style = MaterialTheme.typography.labelMedium,
            color = muted,
        )
        Canvas(
            Modifier.fillMaxWidth().height(160.dp).pointerInput(sorted) {
                detectTapGestures { tap ->
                    if (sorted.isEmpty()) return@detectTapGestures
                    val left = 28.dp.toPx()
                    val right = size.width - 16.dp.toPx()
                    val minDay = sorted.first().day
                    val span = (sorted.last().day - minDay).coerceAtLeast(1)
                    selected = sorted.indices.minByOrNull { i ->
                        val x = if (sorted.size == 1) (left + right) / 2 else left + (sorted[i].day - minDay) * (right - left) / span
                        abs(x - tap.x)
                    }
                }
            },
        ) {
            val left = 28.dp.toPx()
            val right = size.width - 16.dp.toPx()
            val top = 10.dp.toPx()
            val bottom = size.height - 18.dp.toPx()
            fun y(v: Int) = bottom - v * (bottom - top) / 10f

            // Recessive grid with 0 / 5 / 10 labels.
            for (v in listOf(0, 5, 10)) {
                drawLine(grid, Offset(left, y(v)), Offset(right, y(v)), strokeWidth = 1.dp.toPx())
                val t = measurer.measure(v.toString(), axisStyle)
                drawText(t, topLeft = Offset(left - t.size.width - 6.dp.toPx(), y(v) - t.size.height / 2f))
            }
            if (sorted.isEmpty()) return@Canvas

            val minDay = sorted.first().day
            val span = (sorted.last().day - minDay).coerceAtLeast(1)
            val pts = sorted.map { p ->
                val x = if (sorted.size == 1) (left + right) / 2 else left + (p.day - minDay) * (right - left) / span
                Offset(x, y(p.value))
            }
            if (pts.size > 1) {
                val path = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    pts.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(path, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            pts.forEachIndexed { i, o ->
                val r = if (i == selected) 6.dp.toPx() else 4.dp.toPx()
                drawCircle(surface, radius = r + 2.dp.toPx(), center = o) // surface ring
                drawCircle(line, radius = r, center = o)
            }
            // Direct label on the latest point only.
            val last = pts.last()
            val lt = measurer.measure(sorted.last().value.toString(), labelStyle)
            drawText(lt, topLeft = Offset((last.x - lt.size.width / 2f).coerceIn(0f, size.width - lt.size.width), (last.y - lt.size.height - 8.dp.toPx()).coerceAtLeast(0f)))

            // First and last dates on the x axis.
            val d1 = measurer.measure(LocalDate.ofEpochDay(sorted.first().day).format(ShortDate), axisStyle)
            drawText(d1, topLeft = Offset(left, bottom + 4.dp.toPx()))
            if (sorted.size > 1) {
                val d2 = measurer.measure(LocalDate.ofEpochDay(sorted.last().day).format(ShortDate), axisStyle)
                drawText(d2, topLeft = Offset(right - d2.size.width, bottom + 4.dp.toPx()))
            }
        }
    }
}

/** Last 4 weeks of adherence: thin bars, a recessive baseline, the percentage under each bar. */
@Composable
fun WeekBars(weeks: List<WeekAdherence>, currentWeekStart: Long) {
    val bar = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        weeks.forEach { w ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val pct = w.percent
                Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth(0.5f).height(56.dp).clip(MaterialTheme.shapes.extraSmall).background(track))
                    if (pct != null && pct > 0) {
                        Box(
                            Modifier.fillMaxWidth(0.5f).height((56 * pct / 100f).dp)
                                .clip(MaterialTheme.shapes.extraSmall).background(bar),
                        )
                    }
                }
                Text(pct?.let { "$it%" } ?: "–", style = MaterialTheme.typography.labelMedium)
                Text(
                    if (w.weekStart == currentWeekStart) "This wk" else LocalDate.ofEpochDay(w.weekStart).format(ShortDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
