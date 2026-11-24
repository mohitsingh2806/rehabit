package com.mohit.rehabit.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ContentValidatorTest {

    // Gradle runs unit tests from the module directory (app/).
    private val root = File("src/main/assets/content")

    private fun loadBundled(): LoadResult {
        val files = listOf("exercises", "plans").flatMap { dir ->
            (File(root, dir).listFiles() ?: emptyArray())
                .filter { it.name.endsWith(".json") }
                .map { ContentFile("$dir/${it.name}", it.readText()) }
        }
        val version = File(root, "content_version").takeIf { it.exists() }?.readText()
        return ContentLoader.load(version, files, ContentLoader.strictJson)
    }

    /** The gate: a bad content drop fails CI instead of reaching the phone. */
    @Test
    fun bundledContentIsValid() {
        assertTrue("content folder not found at ${root.absolutePath}", root.isDirectory)
        val result = loadBundled()
        val problems = result.problems + ContentValidator.validate(result.bundle) { File(root, it).isFile }
        assertTrue("Content problems:\n" + problems.joinToString("\n"), problems.isEmpty())
        assertTrue("version must be > 0", result.bundle.version > 0)
        assertTrue("no exercises loaded", result.bundle.exercises.isNotEmpty())
    }

    @Test
    fun strictParsingRejectsUnknownFields() {
        val json = """[{"id":"x","name":"X","bodyAreas":["NECK"],"kind":"MOBILITY","position":"SEATED",
            "laterality":"BILATERAL","measure":"REPS","defaultDose":{"reps":5},"why":"w","setup":["s"],
            "cues":["c"],"feel":"f","stopIf":"s","cuess":["typo"]}]"""
        val strict = ContentLoader.load("1", listOf(ContentFile("exercises/a.json", json)), ContentLoader.strictJson)
        assertEquals(1, strict.problems.size)
        val lenient = ContentLoader.load("1", listOf(ContentFile("exercises/a.json", json)), ContentLoader.lenientJson)
        assertTrue(lenient.problems.isEmpty())
        assertEquals(1, lenient.bundle.exercises.size)
    }

    @Test
    fun missingVersionIsReported() {
        val result = ContentLoader.load(null, emptyList(), ContentLoader.strictJson)
        assertTrue(result.problems.any { "content_version" in it })
    }

    @Test
    fun validExerciseHasNoProblems() {
        assertEquals(emptyList<String>(), problemsFor(exercise()))
    }

    @Test
    fun catchesDuplicateIds() {
        val problems = ContentValidator.validate(bundle(exercise(), exercise()))
        assertTrue(problems.any { "Duplicate exercise id" in it })
    }

    @Test
    fun catchesBadId() {
        assertTrue(problemsFor(exercise(id = "Chin Tuck")).any { "kebab-case" in it })
    }

    @Test
    fun catchesTooManyCues() {
        assertTrue(problemsFor(exercise(cues = listOf("a", "b", "c", "d"))).any { "max 3" in it })
    }

    @Test
    fun catchesUnknownLink() {
        assertTrue(problemsFor(exercise(progressions = listOf("nope"))).any { "unknown exercise 'nope'" in it })
    }

    @Test
    fun catchesDoseThatDoesNotMatchMeasure() {
        val e = exercise(measure = Measure.HOLD, dose = Dose(reps = 10))
        assertTrue(problemsFor(e).any { "needs holdSec" in it })
    }

    @Test
    fun catchesSidesOnBilateralExercise() {
        val e = exercise(dose = Dose(reps = 10, sides = Sides.AFFECTED))
        assertTrue(problemsFor(e).any { "'sides' only applies" in it })
    }

    @Test
    fun equipmentNeedsNoEquipmentSubstitute() {
        val banded = exercise(id = "banded", equipment = listOf(Equipment.BAND))
        assertTrue(ContentValidator.validate(bundle(banded)).any { "no no-equipment substitute" in it })

        val plain = exercise(id = "plain", equipment = listOf(Equipment.WALL))
        val fixed = banded.copy(substitutes = listOf("plain"))
        assertEquals(emptyList<String>(), ContentValidator.validate(bundle(fixed, plain)))
    }

    @Test
    fun rehabPlanNeedsRedFlagsAndSchedules() {
        val e = exercise()
        val plan = Plan(
            id = "neck-plan",
            name = "Neck",
            kind = PlanKind.FOCUSED,
            category = PlanCategory.REHAB,
            goal = "g",
            whoFor = "w",
            estMinutes = 10,
            phases = listOf(Phase(id = "p1", name = "Phase 1", items = listOf(PlanItem(exerciseId = e.id)))),
        )
        val problems = ContentValidator.validate(ContentBundle(1, listOf(e), listOf(plan)))
        assertTrue(problems.any { "red flags" in it })
        assertTrue(problems.any { "tracked metric" in it })
        assertTrue(problems.any { "need a schedule" in it })
    }

    @Test
    fun routineItemsAreNeverScheduled() {
        val e = exercise()
        val routine = Plan(
            id = "warm-up",
            name = "Warm-up",
            kind = PlanKind.ON_DEMAND,
            category = PlanCategory.RUNNING,
            goal = "g",
            whoFor = "w",
            estMinutes = 8,
            phases = listOf(
                Phase(
                    id = "main",
                    name = "Main",
                    items = listOf(PlanItem(exerciseId = e.id, schedule = Schedule(slots = listOf(SlotId.MORNING)))),
                ),
            ),
        )
        val problems = ContentValidator.validate(ContentBundle(1, listOf(e), listOf(routine)))
        assertTrue(problems.any { "never scheduled" in it })
    }

    @Test
    fun scheduleNeedsOneSlotPerOccurrence() {
        val e = exercise()
        val plan = Plan(
            id = "p",
            name = "P",
            kind = PlanKind.FOCUSED,
            category = PlanCategory.MOBILITY,
            goal = "g",
            whoFor = "w",
            estMinutes = 5,
            phases = listOf(
                Phase(
                    id = "a",
                    name = "A",
                    items = listOf(PlanItem(exerciseId = e.id, schedule = Schedule(timesPerDay = 2, slots = listOf(SlotId.WAKE)))),
                ),
            ),
        )
        val problems = ContentValidator.validate(ContentBundle(1, listOf(e), listOf(plan)))
        assertTrue(problems.any { "one slot per time a day" in it })
    }

    @Test
    fun showIfMustMatchAChooserAnswer() {
        val e = exercise()
        val plan = Plan(
            id = "p",
            name = "P",
            kind = PlanKind.FOCUSED,
            category = PlanCategory.MOBILITY,
            goal = "g",
            whoFor = "w",
            estMinutes = 5,
            chooser = listOf(
                ChooserQuestion("eases", "Which?", listOf(ChooserOption("a", "A"), ChooserOption("b", "B"))),
            ),
            phases = listOf(
                Phase(
                    id = "x",
                    name = "X",
                    items = listOf(
                        PlanItem(exerciseId = e.id, schedule = Schedule(slots = listOf(SlotId.NIGHT)), showIf = "eases:a"),
                        PlanItem(exerciseId = e.id, schedule = Schedule(slots = listOf(SlotId.NIGHT)), showIf = "eases:zzz"),
                    ),
                ),
            ),
        )
        val problems = ContentValidator.validate(ContentBundle(1, listOf(e), listOf(plan)))
        assertEquals(1, problems.size)
        assertTrue(problems.single().contains("eases:zzz"))
    }

    private fun problemsFor(e: Exercise) = ContentValidator.validate(bundle(e))

    private fun bundle(vararg e: Exercise) = ContentBundle(1, e.toList(), emptyList())

    private fun exercise(
        id: String = "test-exercise",
        measure: Measure = Measure.REPS,
        dose: Dose = Dose(reps = 10),
        cues: List<String> = listOf("Do it well"),
        equipment: List<Equipment> = emptyList(),
        progressions: List<String> = emptyList(),
    ) = Exercise(
        id = id,
        name = "Test",
        bodyAreas = listOf(BodyArea.NECK),
        kind = ExerciseKind.MOBILITY,
        position = Position.SEATED,
        equipment = equipment,
        laterality = Laterality.BILATERAL,
        measure = measure,
        defaultDose = dose,
        why = "Because",
        setup = listOf("Sit"),
        cues = cues,
        feel = "Something",
        stopIf = "It hurts",
        progressions = progressions,
    )
}
