package com.agentframework.data.repository;

import com.agentframework.data.entity.PredictionFeedback;
import com.agentframework.data.entity.ToolPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for PredictionFeedback entities.
 */
@Repository
public interface PredictionFeedbackRepository extends JpaRepository<PredictionFeedback, UUID> {

    /**
     * Find feedback by prediction.
     */
    List<PredictionFeedback> findByPrediction(ToolPrediction prediction);

    /**
     * Find feedback by prediction ID.
     */
    List<PredictionFeedback> findByPredictionId(UUID predictionId);

    /**
     * Find feedback by type.
     */
    List<PredictionFeedback> findByFeedbackType(String feedbackType);

    /**
     * Find feedback by user.
     */
    List<PredictionFeedback> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Find feedback with high accuracy ratings (for positive training samples).
     */
    List<PredictionFeedback> findByAccuracyRatingGreaterThanEqual(Integer minRating);

    /**
     * Find feedback with low accuracy ratings (for identifying issues).
     */
    List<PredictionFeedback> findByAccuracyRatingLessThanEqual(Integer maxRating);

    /**
     * Count feedback by type for statistics.
     */
    @Query("SELECT f.feedbackType, COUNT(f) FROM PredictionFeedback f GROUP BY f.feedbackType")
    List<Object[]> countByFeedbackType();

    /**
     * Calculate average accuracy rating.
     */
    @Query("SELECT AVG(f.accuracyRating) FROM PredictionFeedback f WHERE f.accuracyRating IS NOT NULL")
    Double getAverageAccuracyRating();

    /**
     * Find recent feedback for a specific tool.
     * Useful for tool-specific learning.
     */
    @Query(value = "SELECT * FROM prediction_feedback " +
           "WHERE :toolName = ANY(correct_tools) OR :toolName = ANY(incorrect_tools) " +
           "OR :toolName = ANY(missing_tools) " +
           "ORDER BY created_at DESC", nativeQuery = true)
    List<PredictionFeedback> findByToolName(@Param("toolName") String toolName);

    /**
     * Find feedback created after a certain time.
     * Useful for incremental learning.
     */
    List<PredictionFeedback> findByCreatedAtAfterOrderByCreatedAtDesc(Instant after);

    /**
     * Check if feedback exists for a prediction.
     */
    boolean existsByPredictionId(UUID predictionId);
}
