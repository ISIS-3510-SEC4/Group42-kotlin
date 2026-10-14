package com.example.espoti.recommendation

import com.example.espoti.model.domain.Recommendation
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// =============================================================================
// Strategy pattern - recommendation scoring
// =============================================================================
// A RecommendationScoringStrategy decides how a candidate place is scored.
// Data sources (and through them the ViewModels) only depend on this interface,
// so a new ranking rule is a new class: no consumer has to change.
// =============================================================================

/** Extra information a strategy may use. All fields are optional. */
data class RecommendationContext(
    val userLatitude: Double? = null,
    val userLongitude: Double? = null,
    val preferredCategory: String? = null
)

/** Contract: higher score = better recommendation. */
interface RecommendationScoringStrategy {
    /** Short name, useful for analytics and logs. */
    val name: String

    fun score(candidate: Recommendation, context: RecommendationContext): Double
}

/** Orders and scores a candidate list with the given strategy (best first, stable tie-break). */
fun List<Recommendation>.rankedBy(
    strategy: RecommendationScoringStrategy,
    context: RecommendationContext = RecommendationContext()
): List<Recommendation> =
    map { it.copy(score = strategy.score(it, context)) }
        .sortedWith(
            compareByDescending<Recommendation> { it.score }
                .thenBy { it.name }
                .thenBy { it.placeId }
        )

// ---------------------------------------------------------------------------
// Concrete strategies
// ---------------------------------------------------------------------------

/** Neutral strategy: every place scores 0, so ranking falls back to name order (previous behaviour). */
object AlphabeticalStrategy : RecommendationScoringStrategy {
    override val name = "ALPHABETICAL"
    override fun score(candidate: Recommendation, context: RecommendationContext) = 0.0
}

/** Best rated first (rating 1..5 normalised to 0..1). */
object RatingStrategy : RecommendationScoringStrategy {
    override val name = "RATING"
    override fun score(candidate: Recommendation, context: RecommendationContext) =
        candidate.rating.coerceIn(0, 5) / 5.0
}

/** Closest first: 1 / (1 + km / 5). Without user location or place coordinates it scores 0. */
object ProximityStrategy : RecommendationScoringStrategy {
    override val name = "PROXIMITY"

    override fun score(candidate: Recommendation, context: RecommendationContext): Double {
        val km = distanceKm(candidate, context) ?: return 0.0
        return 1.0 / (1.0 + km / 5.0)
    }

    internal fun distanceKm(candidate: Recommendation, context: RecommendationContext): Double? {
        val lat1 = context.userLatitude ?: return null
        val lng1 = context.userLongitude ?: return null
        val lat2 = candidate.latitude ?: return null
        val lng2 = candidate.longitude ?: return null
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * 6371.0 * asin(sqrt(a))
    }
}

/** Combines several strategies as a weighted sum (e.g. 70 % rating + 30 % proximity). */
class WeightedStrategy(
    private val parts: List<Pair<RecommendationScoringStrategy, Double>>
) : RecommendationScoringStrategy {

    init {
        require(parts.isNotEmpty()) { "At least one strategy is required" }
    }

    override val name = parts.joinToString("+") { "${it.first.name}*${it.second}" }

    override fun score(candidate: Recommendation, context: RecommendationContext): Double {
        val total = parts.sumOf { it.second }
        if (total == 0.0) return 0.0
        return parts.sumOf { (strategy, weight) -> strategy.score(candidate, context) * weight } / total
    }
}
