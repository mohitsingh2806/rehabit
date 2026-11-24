package com.mohit.rehabit.content

import kotlinx.serialization.Serializable

/*
 * Bundled content schema (assets/content). Pure Kotlin: shared by the app and the CI validator test.
 *
 * Enum values are written in JSON by name. Treat them as append-only: renaming or removing a value
 * breaks existing content files and anything stored that references it.
 */

@Serializable
enum class BodyArea {
    FOOT, ANKLE, CALF, SHIN, KNEE, QUAD, HAMSTRING, HIP, GLUTE, ADDUCTOR,
    LUMBAR, THORACIC, NECK, SHOULDER, ELBOW, WRIST, CORE, FULL_BODY,
}

@Serializable
enum class ExerciseKind {
    STRETCH_STATIC, STRETCH_DYNAMIC, MOBILITY, STRENGTH, ISOMETRIC, ACTIVATION,
    BALANCE, PLYOMETRIC, NERVE_GLIDE, SOFT_TISSUE, CARDIO,

    /** A yoga pose (usually held, often breath-paced). */
    YOGA,

    /** Paced breathing / relaxation. */
    BREATHING,
}

@Serializable
enum class Position { STANDING, SEATED, FLOOR_SUPINE, FLOOR_PRONE, SIDE_LYING, QUADRUPED, KNEELING }

@Serializable
enum class Equipment {
    WALL, CHAIR, STEP, TOWEL, MAT, BAND, LOOP_BAND, FOAM_ROLLER, BALL, STRAP, DUMBBELL, BENCH, DOOR,

    /** A broom handle or similar, for assisted shoulder exercises. */
    STICK;

    /** Found in any home or office, so it never needs a no-equipment substitute. */
    val everyday: Boolean get() = this in EVERYDAY

    private companion object {
        val EVERYDAY = setOf(WALL, CHAIR, STEP, TOWEL, MAT, STICK)
    }
}

/** BILATERAL: both sides at once. EACH_SIDE: one side, then the other. ALTERNATING: left/right within a set. */
@Serializable
enum class Laterality { BILATERAL, EACH_SIDE, ALTERNATING }

/** What one rep / set is measured in; decides which [Dose] fields are required. */
@Serializable
enum class Measure { REPS, HOLD, REPS_WITH_HOLD, TIME, DISTANCE }

/** For EACH_SIDE exercises: do both sides, or only the side chosen when following a plan. */
@Serializable
enum class Sides { BOTH, AFFECTED }

/**
 * How much. A plan item's dose replaces the exercise's default dose entirely (fields are not merged).
 * For ALTERNATING / EACH_SIDE exercises, [reps] and [holdSec] are per side.
 */
@Serializable
data class Dose(
    val sets: Int = 1,
    val reps: Int? = null,
    val holdSec: Int? = null,
    val durationSec: Int? = null,
    val distanceM: Int? = null,
    /** Rest between sets. */
    val restSec: Int? = null,
    /**
     * Seconds per phase, e.g. "3-2-3" = up, hold, down. Drives the metronome. On a HOLD or TIME
     * dose it paces breathing instead, e.g. "4-6" = breathe in 4 s, out 6 s.
     */
    val tempo: String? = null,
    val sides: Sides? = null,
    /** Free text, e.g. "bodyweight", "+ loaded backpack". */
    val load: String? = null,
    /** Free text, e.g. "gentle stretch, 3/10 at most". */
    val effort: String? = null,
)

@Serializable
enum class MediaType { IMAGE, VIDEO_LINK }

/** IMAGE: path inside assets/content (e.g. "images/chin-tuck.jpg"). VIDEO_LINK: a verified https URL. */
@Serializable
data class Media(val type: MediaType, val url: String, val credit: String? = null)

@Serializable
data class Exercise(
    /** Stable kebab-case slug. Never rename or reuse once shipped: logs reference it. */
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val bodyAreas: List<BodyArea>,
    val kind: ExerciseKind,
    val position: Position,
    val deskFriendly: Boolean = false,
    val trailFriendly: Boolean = false,
    val equipment: List<Equipment> = emptyList(),
    val laterality: Laterality,
    val measure: Measure,
    val defaultDose: Dose,
    /** Why it helps, in plain words. */
    val why: String,
    val setup: List<String>,
    /** At most 3, short and imperative: they are spoken aloud by the player. */
    val cues: List<String>,
    val mistakes: List<String> = emptyList(),
    /** Where you should feel it. */
    val feel: String,
    /** When to stop (and where you must NOT feel it). */
    val stopIf: String,
    val cautions: List<String> = emptyList(),
    val regressions: List<String> = emptyList(),
    val progressions: List<String> = emptyList(),
    /** Equivalent alternatives (e.g. no-equipment versions). */
    val substitutes: List<String> = emptyList(),
    val media: List<Media> = emptyList(),
    /** Author, year, title, source. Only sources known to exist. */
    val references: List<String> = emptyList(),
    /** False until the owner has reviewed it; shown as a "Not reviewed" badge. */
    val reviewed: Boolean = false,
)

/** ON_DEMAND is shown as a Routine (do it whenever). FOCUSED is shown as a Plan (scheduled, tracked). */
@Serializable
enum class PlanKind { ON_DEMAND, FOCUSED }

@Serializable
enum class PlanCategory {
    RUNNING, HIKING, MOBILITY, STRENGTH, DESK, SPORT, REHAB,

    YOGA,

    /** Made by the user in the builder. */
    CUSTOM,
}

/** User-made exercises and plans use this id prefix, so they never clash with bundled ones. */
const val USER_ID_PREFIX = "my-"

val Exercise.isUser: Boolean get() = id.startsWith(USER_ID_PREFIX)
val Plan.isUser: Boolean get() = id.startsWith(USER_ID_PREFIX)

/** Time slots of the day. The user sets each slot's time in Settings. */
@Serializable
enum class SlotId { WAKE, MORNING, DESK, MIDDAY, EVENING, NIGHT, ANYTIME }

@Serializable
enum class DayRule { DAILY, EVERY_N_DAYS, WEEKDAYS }

@Serializable
enum class Weekday { MON, TUE, WED, THU, FRI, SAT, SUN }

@Serializable
data class Schedule(
    val timesPerDay: Int = 1,
    val days: DayRule = DayRule.DAILY,
    /** Required for EVERY_N_DAYS (>= 2). Fixed calendar from the plan's start date. */
    val everyNDays: Int? = null,
    /** Required for WEEKDAYS. */
    val weekdays: List<Weekday> = emptyList(),
    /** Suggested slot for each occurrence; size must equal [timesPerDay]. */
    val slots: List<SlotId>,
    /** Moves to the next day if the user ran today (e.g. heavy tendon loading). */
    val notOnRunDay: Boolean = false,
)

/** A "Which sounds like you?" question asked when following a plan; answers pick plan items. */
@Serializable
data class ChooserQuestion(val id: String, val text: String, val options: List<ChooserOption>)

@Serializable
data class ChooserOption(val id: String, val label: String, val hint: String? = null)

@Serializable
data class PlanItem(
    val exerciseId: String,
    /** Null = the exercise's default dose. */
    val dose: Dose? = null,
    /** Required in FOCUSED plans, absent in ON_DEMAND ones. */
    val schedule: Schedule? = null,
    /** Optional items never count against adherence and are dropped by "Core only". */
    val core: Boolean = true,
    val note: String? = null,
    /** "questionId:optionId": the item is only included for that chooser answer. */
    val showIf: String? = null,
)

@Serializable
data class Phase(
    val id: String,
    val name: String,
    val durationHint: String? = null,
    val entryCriteria: String? = null,
    val exitCriteria: String? = null,
    val items: List<PlanItem>,
)

@Serializable
data class Plan(
    val id: String,
    val name: String,
    val kind: PlanKind,
    val category: PlanCategory,
    val goal: String,
    val whoFor: String,
    val notFor: List<String> = emptyList(),
    val estMinutes: Int,
    /** Routines and simple plans have exactly one phase. */
    val phases: List<Phase>,
    /** e.g. "Pain on your first steps in the morning (0-10)". Required for REHAB plans. */
    val trackedMetric: String? = null,
    /** Required for REHAB plans. */
    val redFlags: List<String> = emptyList(),
    val painRules: String? = null,
    /** Exercise ids to avoid while following this plan (warned about when combining). */
    val avoid: List<String> = emptyList(),
    /** Asked when following the plan; answers include items via [PlanItem.showIf]. */
    val chooser: List<ChooserQuestion> = emptyList(),
    /** A flowing sequence (e.g. sun salutation): short transitions, no long "get ready" between poses. */
    val flow: Boolean = false,
    val references: List<String> = emptyList(),
    val reviewed: Boolean = false,
)

data class ContentBundle(
    val version: Int,
    val exercises: List<Exercise>,
    val plans: List<Plan>,
) {
    val exercisesById: Map<String, Exercise> by lazy { exercises.associateBy { it.id } }
    val plansById: Map<String, Plan> by lazy { plans.associateBy { it.id } }

    companion object {
        val EMPTY = ContentBundle(0, emptyList(), emptyList())
    }
}
