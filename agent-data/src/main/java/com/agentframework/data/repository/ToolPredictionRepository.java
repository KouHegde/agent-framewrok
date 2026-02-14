package com.agentframework.data.repository;

import com.agentframework.data.entity.ToolPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for ToolPrediction entities.
 */
@Repository
public interface ToolPredictionRepository extends JpaRepository<ToolPrediction, UUID> {

    /**
     * Find predictions by user ID.
     */
    List<ToolPrediction> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Find predictions by tenant ID.
     */
    List<ToolPrediction> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    /**
     * Find predictions by session ID.
     */
    List<ToolPrediction> findBySessionIdOrderByCreatedAtDesc(String sessionId);

    /**
     * Find predictions by status.
     */
    List<ToolPrediction> findByStatus(String status);

    /**
     * Find predictions by method.
     */
    List<ToolPrediction> findByPredictionMethod(String predictionMethod);

    /**
     * Find predictions created after a certain time.
     */
    List<ToolPrediction> findByCreatedAtAfterOrderByCreatedAtDesc(Instant after);

    /**
     * Find predictions that haven't received feedback yet.
     * Useful for identifying predictions that need follow-up.
     */
    @Query("SELECT p FROM ToolPrediction p WHERE p.status = 'completed' " +
           "AND NOT EXISTS (SELECT f FROM PredictionFeedback f WHERE f.prediction = p)")
    List<ToolPrediction> findPredictionsWithoutFeedback();

    /**
     * Count predictions by method for statistics.
     */
    @Query("SELECT p.predictionMethod, COUNT(p) FROM ToolPrediction p GROUP BY p.predictionMethod")
    List<Object[]> countByPredictionMethod();

    /**
     * Find similar predictions using vector similarity.
     * This uses pgvector's <=> operator for cosine distance.
     * Note: This is a native query since JPA doesn't support pgvector operators.
     */
    @Query(value = "SELECT * FROM tool_predictions " +
                   "WHERE query_embedding IS NOT NULL " +
                   "ORDER BY query_embedding <=> cast(:embedding as vector) " +
                   "LIMIT :limit", nativeQuery = true)
    List<ToolPrediction> findSimilarByEmbedding(@Param("embedding") String embedding, 
                                                 @Param("limit") int limit);

    /**
     * Find exact query match (for caching).
     */
    List<ToolPrediction> findByQueryAndStatus(String query, String status);
}
