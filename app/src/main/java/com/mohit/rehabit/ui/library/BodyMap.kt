package com.mohit.rehabit.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mohit.rehabit.content.BodyArea
import com.mohit.rehabit.ui.common.label

/** A tappable body part, in a 100 x 150 coordinate box. */
private data class Region(val area: BodyArea?, val rect: Rect, val round: Float = 4f)

private fun r(area: BodyArea?, x: Float, y: Float, w: Float, h: Float, round: Float = 4f) = Region(area, Rect(x, y, x + w, y + h), round)

/** Both legs/arms share an area, so they're listed twice (left and right). */
private val Front = listOf(
    r(null, 42f, 2f, 16f, 16f, 8f), // head
    r(BodyArea.NECK, 45f, 18f, 10f, 6f),
    r(BodyArea.SHOULDER, 26f, 24f, 15f, 10f, 5f), r(BodyArea.SHOULDER, 59f, 24f, 15f, 10f, 5f),
    r(BodyArea.THORACIC, 37f, 25f, 26f, 16f), // chest
    r(BodyArea.CORE, 39f, 41f, 22f, 16f),
    r(null, 24f, 34f, 9f, 20f), r(null, 67f, 34f, 9f, 20f), // upper arms
    r(BodyArea.ELBOW, 23f, 54f, 9f, 7f), r(BodyArea.ELBOW, 68f, 54f, 9f, 7f),
    r(BodyArea.WRIST, 21f, 61f, 9f, 16f), r(BodyArea.WRIST, 70f, 61f, 9f, 16f),
    r(BodyArea.HIP, 37f, 57f, 26f, 11f),
    r(BodyArea.QUAD, 37f, 68f, 12f, 26f), r(BodyArea.QUAD, 51f, 68f, 12f, 26f),
    r(BodyArea.ADDUCTOR, 47.5f, 70f, 5f, 18f, 2f),
    r(BodyArea.KNEE, 37.5f, 94f, 11f, 8f, 4f), r(BodyArea.KNEE, 51.5f, 94f, 11f, 8f, 4f),
    r(BodyArea.SHIN, 38.5f, 102f, 9f, 28f), r(BodyArea.SHIN, 52.5f, 102f, 9f, 28f),
    r(BodyArea.ANKLE, 38.5f, 130f, 9f, 6f), r(BodyArea.ANKLE, 52.5f, 130f, 9f, 6f),
    r(BodyArea.FOOT, 35f, 136f, 13f, 7f, 3f), r(BodyArea.FOOT, 52f, 136f, 13f, 7f, 3f),
)

private val Back = listOf(
    r(null, 42f, 2f, 16f, 16f, 8f),
    r(BodyArea.NECK, 45f, 18f, 10f, 6f),
    r(BodyArea.SHOULDER, 26f, 24f, 15f, 10f, 5f), r(BodyArea.SHOULDER, 59f, 24f, 15f, 10f, 5f),
    r(BodyArea.THORACIC, 37f, 25f, 26f, 18f), // upper back
    r(BodyArea.LUMBAR, 39f, 43f, 22f, 14f),
    r(null, 24f, 34f, 9f, 20f), r(null, 67f, 34f, 9f, 20f),
    r(BodyArea.ELBOW, 23f, 54f, 9f, 7f), r(BodyArea.ELBOW, 68f, 54f, 9f, 7f),
    r(BodyArea.WRIST, 21f, 61f, 9f, 16f), r(BodyArea.WRIST, 70f, 61f, 9f, 16f),
    r(BodyArea.GLUTE, 37f, 57f, 26f, 13f),
    r(BodyArea.HAMSTRING, 37f, 70f, 12f, 24f), r(BodyArea.HAMSTRING, 51f, 70f, 12f, 24f),
    r(BodyArea.KNEE, 37.5f, 94f, 11f, 8f, 4f), r(BodyArea.KNEE, 51.5f, 94f, 11f, 8f, 4f),
    r(BodyArea.CALF, 38.5f, 102f, 9f, 28f), r(BodyArea.CALF, 52.5f, 102f, 9f, 28f),
    r(BodyArea.ANKLE, 38.5f, 130f, 9f, 6f), r(BodyArea.ANKLE, 52.5f, 130f, 9f, 6f),
    r(BodyArea.FOOT, 35f, 136f, 13f, 7f, 3f), r(BodyArea.FOOT, 52f, 136f, 13f, 7f, 3f),
)

/**
 * Front and back body figures. Tap a part to filter exercises by that area (tap again to clear).
 * Parts with no exercises are greyed out; the selected part is filled and named below.
 */
@Composable
fun BodyMap(selected: BodyArea?, available: Set<BodyArea>, onSelect: (BodyArea?) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("Front" to Front, "Back" to Back).forEach { (title, regions) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    Canvas(
                        Modifier
                            .fillMaxWidth(0.9f)
                            .aspectRatio(100f / 150f)
                            .semantics { contentDescription = "Body map, $title. Tap a body part to filter exercises." }
                            .pointerInput(selected, available) {
                                detectTapGestures { tap ->
                                    val sx = size.width / 100f
                                    val sy = size.height / 150f
                                    val p = Offset(tap.x / sx, tap.y / sy)
                                    val hit = regions.lastOrNull { it.area != null && it.rect.inflate(1.5f).contains(p) }?.area
                                    if (hit != null && hit in available) onSelect(if (hit == selected) null else hit)
                                }
                            },
                    ) {
                        regions.forEach { reg ->
                            val fill = when {
                                reg.area == null -> colors.surfaceVariant
                                reg.area == selected -> colors.primary
                                reg.area in available -> colors.primaryContainer
                                else -> colors.surfaceVariant
                            }
                            drawRegion(reg, fill, colors.outlineVariant)
                        }
                    }
                }
            }
        }
        Text(
            selected?.let { "Showing: ${it.label} (tap again to clear)" } ?: "Tap a body part",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun DrawScope.drawRegion(reg: Region, fill: Color, outline: Color) {
    val sx = size.width / 100f
    val sy = size.height / 150f
    val topLeft = Offset(reg.rect.left * sx, reg.rect.top * sy)
    val sz = Size(reg.rect.width * sx, reg.rect.height * sy)
    val radius = CornerRadius(reg.round * sx, reg.round * sx)
    drawRoundRect(fill, topLeft, sz, radius)
    drawRoundRect(outline, topLeft, sz, radius, style = Stroke(width = 1f))
}
