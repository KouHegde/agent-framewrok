package com.agentframework.data.repository;

import com.agentframework.data.entity.TrainingSample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for TrainingSample entities.
 */
@Repository
public interface TrainingSampleRepository extends JpaRepository<TrainingSample, UUID> {

    /**
     * Find samples by source.
     */
    List<TrainingSample> findBySource(String source);

    /**
     * Find verified samples.
     */
    List<TrainingSample> findByVerifiedTrue();

    /**
     * Find unverified samples.
     */
    List<TrainingSample> findByVerifiedFalse();

    /**
     * Find samples not yet used in training.
     */
    List<TrainingSample> findByUsedInTrainingFalse();

    /**
     * Find samples used in a specific training run.
     */
    List<TrainingSample> findByLastTrainingRun(String trainingRunId);

    /**
     * Find samples created after a certain time.
     */
    List<TrainingSample> findByCreatedAtAfter(Instant after);

    /**
     * Find samples for specific tools.
     */
    @Query(value = "SELECT * FROM training_samples WHERE :toolName = ANY(tools)", nativeQuery = true)
    List<TrainingSample> findByTool(@Param("toolName") String toolName);

    /**
     * Find samples that haven't been used for training yet.
     * Ordered by confidence level (high confidence first).
     */
    @Query("SELECT s FROM TrainingSample s WHERE s.usedInTraining = false " +
           "ORDER BY s.confidenceLevel DESC, s.createdAt ASC")
    List<TrainingSample> findUnusedSamplesOrderedByConfidence();

    /**
     * Find high-quality verified samples for training.
     */
    @Query("SELECT s FROM TrainingSample s WHERE s.verified = true " +
           "AND s.confidenceLevel >= :minConfidence " +
           "ORDER BY s.createdAt DESC")
    List<TrainingSample> findHighQualitySamples(@Param("minConfidence") java.math.BigDecimal minConfidence);

    /**
     * Count samples by source.
     */
    @Query("SELECT s.source, COUNT(s) FROM TrainingSample s GROUP BY s.source")
    List<Object[]> countBySource();

    /**
     * Mark samples as used in training.
     */
    @Modifying
    @Query("UPDATE TrainingSample s SET s.usedInTraining = true, s.lastTrainingRun = :runId " +
           "WHERE s.id IN :ids")
    int markAsUsedInTraining(@Param("ids") List<UUID> ids, @Param("runId") String runId);

    /**
     * Find similar samples using vector similarity.
     * Uses pgvector's <=> operator for cosine distance.
     */
    @Query(value = "SELECT * FROM training_samples " +
                   "WHERE query_embedding IS NOT NULL " +
                   "ORDER BY query_embedding <=> cast(:embedding as vector) " +
                   "LIMIT :limit", nativeQuery = true)
    List<TrainingSample> findSimilarByEmbedding(@Param("embedding") String embedding, 
                                                 @Param("limit") int limit);

    /**
     * Get total count of training samples ready for training.
     */
    @Query("SELECT COUNT(s) FROM TrainingSample s WHERE s.usedInTraining = false")
    long countUnusedSamples();

    /**
     * Check if a similar query already exists.
     */
    boolean existsByQuery(String query);
}
