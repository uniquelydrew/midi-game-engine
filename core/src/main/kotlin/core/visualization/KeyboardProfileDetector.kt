package core.visualization

object KeyboardProfileDetector {
    private val profiles = KeyboardProfile.entries.sortedBy { it.keyCount }

    fun detect(deviceDescription: String?, observedPitches: Collection<Int>): KeyboardProfile {
        val metadata = deviceDescription.orEmpty().lowercase()
        profiles.firstOrNull { profile ->
            Regex("\\b${profile.keyCount}\\s*[- ]?key").containsMatchIn(metadata) ||
                Regex("\\b${profile.keyCount}\\s*keys?\\b").containsMatchIn(metadata)
        }?.let { return it }

        val pitches = observedPitches.filter { it in 21..108 }
        if (pitches.isNotEmpty()) {
            val first = (pitches.minOrNull()!! - 2).coerceAtLeast(21)
            val last = (pitches.maxOrNull()!! + 2).coerceAtMost(108)
            profiles.firstOrNull { it.firstPitch <= first && it.lastPitch >= last }?.let { return it }
        }

        return KeyboardProfile.KEYS_88
    }
}
