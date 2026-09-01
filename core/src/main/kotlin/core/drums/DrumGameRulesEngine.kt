package core.drums

enum class StarRating { One, Two, Three, Four, Five }
data class DrumGameState(val score: Long = 0, val combo: Int = 0, val maxCombo: Int = 0, val multiplier: Int = 1, val perfectStreak: Int = 0, val maxPerfectStreak: Int = 0, val accuracyPercent: Double = 0.0, val starRating: StarRating = StarRating.One)

class DrumGameRulesEngine {
    private var score = 0L
    private var combo = 0
    private var maxCombo = 0
    private var perfectStreak = 0
    private var maxPerfectStreak = 0
    private var weighted = 0
    private var judged = 0

    fun onJudgment(result: DrumJudgmentResult) {
        judged++
        val points = when (result.judgment) { DrumJudgment.Perfect -> 100; DrumJudgment.Great -> 80; DrumJudgment.Good -> 50; DrumJudgment.Miss -> 0 }
        if (result.judgment == DrumJudgment.Miss) { combo = 0; perfectStreak = 0 } else {
            combo++
            maxCombo = maxOf(maxCombo, combo)
            if (result.judgment == DrumJudgment.Perfect) { perfectStreak++; maxPerfectStreak = maxOf(maxPerfectStreak, perfectStreak) } else perfectStreak = 0
            score += points.toLong() * multiplierFor(combo)
        }
        weighted += points
    }
    fun snapshot(): DrumGameState {
        val accuracy = if (judged == 0) 0.0 else weighted * 100.0 / (judged * 100)
        return DrumGameState(score, combo, maxCombo, multiplierFor(combo), perfectStreak, maxPerfectStreak, accuracy, stars(accuracy))
    }
    fun reset() { score = 0; combo = 0; maxCombo = 0; perfectStreak = 0; maxPerfectStreak = 0; weighted = 0; judged = 0 }
    private fun multiplierFor(value: Int) = when { value >= 50 -> 4; value >= 25 -> 3; value >= 10 -> 2; else -> 1 }
    private fun stars(accuracy: Double) = when { accuracy >= 97 -> StarRating.Five; accuracy >= 90 -> StarRating.Four; accuracy >= 80 -> StarRating.Three; accuracy >= 65 -> StarRating.Two; else -> StarRating.One }
}
