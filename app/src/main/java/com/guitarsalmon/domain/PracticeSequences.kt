package com.guitarsalmon.domain

/** A named chord sequence a learner can practise. */
data class PracticeSequence(
    val name: String,
    val chords: List<String>,
    val description: String,
)

/** Built-in sequences, ordered roughly by difficulty. */
object PracticeSequences {

    val presets: List<PracticeSequence> = listOf(
        PracticeSequence(
            name = "First chords",
            chords = listOf("Em", "Am"),
            description = "Two minor shapes that share a finger pattern",
        ),
        PracticeSequence(
            name = "G - C - D",
            chords = listOf("G", "C", "D"),
            description = "The backbone of countless songs",
        ),
        PracticeSequence(
            name = "G - C - D - Em",
            chords = listOf("G", "C", "D", "Em"),
            description = "Adds a minor chord to the classic three",
        ),
        PracticeSequence(
            name = "Pop progression",
            chords = listOf("C", "G", "Am", "F"),
            description = "I - V - vi - IV, the four-chord song",
        ),
        PracticeSequence(
            name = "Blues in A",
            chords = listOf("A7", "D7", "E7"),
            description = "Dominant sevenths for a twelve-bar blues",
        ),
        PracticeSequence(
            name = "Jazz ii - V - I",
            chords = listOf("Dm7", "G7", "Cmaj7"),
            description = "The most common turnaround in jazz",
        ),
    )

    /** Chords offered when the user wants to drill a single shape. */
    val singleChordOptions: List<String> = listOf(
        "C", "G", "D", "A", "E", "F",
        "Am", "Em", "Dm", "Bm",
        "A7", "D7", "E7", "G7",
        "Cmaj7", "Fmaj7", "Am7", "Dm7", "Em7",
        "Dsus2", "Dsus4", "Asus2", "Asus4",
    )
}
