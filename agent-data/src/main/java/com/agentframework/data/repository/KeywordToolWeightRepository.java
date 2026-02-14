package com.agentframework.data.repository;

import com.agentframework.data.entity.KeywordToolWeight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for KeywordToolWeight entities.
 */
@Repository
public interface KeywordToolWeightRepository extends JpaRepository<KeywordToolWeight, UUID> {

    /**
     * Find weight by keyword and tool name.
     */
    Optional<KeywordToolWeight> findByKeywordAndToolName(String keyword, String toolName);

    /**
     * Find all weights for a keyword.
     */
    List<KeywordToolWeight> findByKeywordOrderByWeightDesc(String keyword);

    /**
     * Find all weights for a tool.
     */
    List<KeywordToolWeight> findByToolNameOrderByWeightDesc(String toolName);

    /**
     * Find high-weight associations.
     */
    List<KeywordToolWeight> findByWeightGreaterThanEqualOrderByWeightDesc(BigDecimal minWeight);

    /**
     * Find weights for multiple keywords.
     * Useful for batch lookup during prediction.
     */
    List<KeywordToolWeight> findByKeywordIn(List<String> keywords);

    /**
     * Get top N tools for a keyword.
     */
    @Query("SELECT k FROM KeywordToolWeight k WHERE k.keyword = :keyword " +
           "ORDER BY k.weight DESC")
    List<KeywordToolWeight> findTopToolsForKeyword(@Param("keyword") String keyword);

    /**
     * Get top N keywords for a tool.
     */
    @Query("SELECT k FROM KeywordToolWeight k WHERE k.toolName = :toolName " +
           "ORDER BY k.weight DESC")
    List<KeywordToolWeight> findTopKeywordsForTool(@Param("toolName") String toolName);

    /**
     * Find associations with enough occurrences for reliable weights.
     */
    List<KeywordToolWeight> findByOccurrenceCountGreaterThanEqual(Integer minOccurrences);

    /**
     * Get weighted tool scores for multiple keywords.
     * Returns tool name and sum of weights for given keywords.
     */
    @Query("SELECT k.toolName, SUM(k.weight) as totalWeight " +
           "FROM KeywordToolWeight k " +
           "WHERE k.keyword IN :keywords " +
           "GROUP BY k.toolName " +
           "ORDER BY totalWeight DESC")
    List<Object[]> getWeightedToolScoresForKeywords(@Param("keywords") List<String> keywords);

    /**
     * Check if an association exists.
     */
    boolean existsByKeywordAndToolName(String keyword, String toolName);
}
