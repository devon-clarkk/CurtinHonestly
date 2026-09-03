package com.curtinhonestly.backend.service.recommendation;

import com.curtinhonestly.backend.domain.ReviewTag;

import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.COLLAB_WEIGHT;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.MIN_OVERLAP;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.OVERLAP_SHRINK;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.PROFILE_WEIGHT;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.WORKLOAD_SCALE;

/** Pure similarity functions between two {@link TasteProfile}s. */
public final class UserSimilarity {

    private UserSimilarity() {
    }

    /**
     * Blend of collaborative similarity (shared units) and a small profile term
     * (workload tolerance and tag preferences) so that students with no shared
     * units can still be weak neighbours. Result in [-1, 1].
     */
    public static double similarity(TasteProfile a, TasteProfile b) {
        double blended = COLLAB_WEIGHT * collaborative(a, b) + PROFILE_WEIGHT * profile(a, b);
        return Math.max(-1.0, Math.min(1.0, blended));
    }

    /**
     * Cosine similarity over the units both students reviewed, shrunk by
     * overlap / (overlap + OVERLAP_SHRINK). Zero when they share fewer than
     * MIN_OVERLAP units or when either side is flat over the overlap.
     */
    static double collaborative(TasteProfile a, TasteProfile b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        int overlap = 0;
        for (var entry : a.affinities().entrySet()) {
            Double other = b.affinities().get(entry.getKey());
            if (other == null) {
                continue;
            }
            overlap++;
            double x = entry.getValue();
            dot += x * other;
            normA += x * x;
            normB += other * other;
        }
        if (overlap < MIN_OVERLAP || normA == 0 || normB == 0) {
            return 0;
        }
        double cosine = dot / (Math.sqrt(normA) * Math.sqrt(normB));
        return cosine * (overlap / (overlap + OVERLAP_SHRINK));
    }

    /** Profile similarity in [0, 1]: mean of a workload-tolerance term and a tag-preference cosine. */
    static double profile(TasteProfile a, TasteProfile b) {
        double workloadTerm = 0;
        if (a.likedWorkloadMean() != null && b.likedWorkloadMean() != null) {
            double gap = Math.abs(a.likedWorkloadMean() - b.likedWorkloadMean());
            workloadTerm = Math.max(0, 1 - gap / WORKLOAD_SCALE);
        }

        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (ReviewTag tag : ReviewTag.values()) {
            double x = a.likedTagShares().getOrDefault(tag, 0.0);
            double y = b.likedTagShares().getOrDefault(tag, 0.0);
            dot += x * y;
            normA += x * x;
            normB += y * y;
        }
        double tagTerm = (normA == 0 || normB == 0) ? 0 : dot / (Math.sqrt(normA) * Math.sqrt(normB));

        return (workloadTerm + tagTerm) / 2;
    }
}
