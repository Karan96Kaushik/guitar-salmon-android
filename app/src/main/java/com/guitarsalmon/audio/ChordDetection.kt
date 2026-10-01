package com.guitarsalmon.audio

/**
 * One frame of analysis output.
 *
 * @param chordName detected chord, or `null` when the signal is too quiet or no
 *   template matched convincingly.
 * @param confidence cosine similarity of the best matching template, 0..1.
 * @param rms level of the analysis window, 0..1.
 * @param chroma 12 pitch class energies (C..B), normalised so the largest is 1.
 */
data class ChordDetection(
    val chordName: String?,
    val confidence: Float,
    val rms: Float,
    val chroma: FloatArray,
) {
    val hasChord: Boolean get() = chordName != null

    // chroma is a FloatArray, so the generated equals/hashCode would compare by
    // identity. Compare by content instead, otherwise Compose and StateFlow treat
    // every frame as a change even when nothing moved.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChordDetection) return false
        return chordName == other.chordName &&
            confidence == other.confidence &&
            rms == other.rms &&
            chroma.contentEquals(other.chroma)
    }

    override fun hashCode(): Int {
        var result = chordName?.hashCode() ?: 0
        result = 31 * result + confidence.hashCode()
        result = 31 * result + rms.hashCode()
        result = 31 * result + chroma.contentHashCode()
        return result
    }

    companion object {
        val NONE = ChordDetection(
            chordName = null,
            confidence = 0f,
            rms = 0f,
            chroma = FloatArray(NativeAudioEngine.CHROMA_SIZE),
        )
    }
}
