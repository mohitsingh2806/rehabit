package com.mohit.rehabit.ui.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohit.rehabit.content.BodyArea
import com.mohit.rehabit.content.Equipment
import com.mohit.rehabit.content.Exercise
import com.mohit.rehabit.content.ExerciseKind
import com.mohit.rehabit.content.Position
import com.mohit.rehabit.domain.doseText

/** Inner screens let the outer scaffold / top app bar handle system insets. */
val NoInsets = WindowInsets(0, 0, 0, 0)

fun Exercise.defaultDoseText(): String = doseText(defaultDose, measure, laterality)

/** "FULL_BODY" → "Full body" */
private fun Enum<*>.pretty(): String = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

val BodyArea.label: String
    get() = when (this) {
        BodyArea.LUMBAR -> "Lower back"
        BodyArea.THORACIC -> "Mid back"
        else -> pretty()
    }

val ExerciseKind.label: String
    get() = when (this) {
        ExerciseKind.STRETCH_STATIC -> "Stretch"
        ExerciseKind.STRETCH_DYNAMIC -> "Dynamic stretch"
        ExerciseKind.NERVE_GLIDE -> "Nerve glide"
        ExerciseKind.SOFT_TISSUE -> "Soft tissue"
        else -> pretty()
    }

val Position.label: String
    get() = when (this) {
        Position.FLOOR_SUPINE -> "Lying on your back"
        Position.FLOOR_PRONE -> "Lying face down"
        Position.SIDE_LYING -> "Lying on your side"
        Position.QUADRUPED -> "Hands and knees"
        else -> pretty()
    }

val Equipment.label: String
    get() = when (this) {
        Equipment.BAND -> "Resistance band"
        Equipment.LOOP_BAND -> "Loop band"
        Equipment.FOAM_ROLLER -> "Foam roller"
        Equipment.DUMBBELL -> "Dumbbell / weight"
        else -> pretty()
    }

/** Shown on content the owner hasn't reviewed yet. */
@Composable
fun NotReviewedBadge(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Text(
            "Not reviewed",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
