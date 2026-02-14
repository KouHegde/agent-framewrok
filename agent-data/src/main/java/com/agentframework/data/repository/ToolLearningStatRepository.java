package com.agentframework.data.repository;

import com.agentframework.data.entity.ToolLearningStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ToolLearningStat entities.
 */
@Repository
public interface ToolLearningStatRepository extends JpaRepository<ToolLearningStat, UUID> {

    /**
     * Find stats by tool name.
     */
    Optional<ToolLearningStat> findByToolName(String toolName);

    /**
     * Check if stats exist for a tool.
     */
    boolean existsByToolName(String toolName);

    /**
     * Find tools with high F1 scores (well-performing tools).
     */
    List<ToolLearningStat> findByF1ScoreGreaterThanEqualOrderByF1ScoreDesc(BigDecimal minF1Score);

    /**
     * Find tools with low F1 scores (poorly performing tools).
     */
    List<ToolLearningStat> findByF1ScoreLessThanOrderByF1ScoreAsc(BigDecimal maxF1Score);

    /**
     * Find tools with enough data for reliable statistics.
     */
    List<ToolLearningStat> findByTotalPredictionsGreaterThanEqual(Integer minPredictions);

    /**
     * Get top N tools by F1 score.
     */
    List<ToolLearningStat> findTop10ByOrderByF1ScoreDesc();

    /**
     * Get bottom N tools by F1 score (needs improvement).
     */
    List<ToolLearningStat> findTop10ByTotalPredictionsGreaterThanOrderByF1ScoreAsc(Integer minPredictions);

    /**
     * Get overall statistics.
     */
    @Query("SELECT " +
           "COUNT(s), " +
           "AVG(s.precisionScore), " +
           "AVG(s.recallScore), " +
           "AVG(s.f1Score), " +
           "SUM(s.totalPredictions), " +
           "SUM(s.correctPredictions) " +
           "FROM ToolLearningStat s")
    List<Object[]> getOverallStatistics();

    /**
     * Find tools by name pattern (for category-based queries).
     */
    @Query("SELECT s FROM ToolLearningStat s WHERE s.toolName LIKE :pattern ORDER BY s.f1Score DESC")
    List<ToolLearningStat> findByToolNamePattern(@Param("pattern") String pattern);

    /**
     * Get stats for multiple tools.
     */
    List<ToolLearningStat> findByToolNameIn(List<String> toolNames);
}
