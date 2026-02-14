package com.agentframework.data.repository;

import com.agentframework.data.entity.ModelTrainingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ModelTrainingStatus entities.
 */
@Repository
public interface ModelTrainingStatusRepository extends JpaRepository<ModelTrainingStatus, UUID> {

    /**
     * Find status by model name.
     */
    Optional<ModelTrainingStatus> findByModelName(String modelName);

    /**
     * Find the default model status.
     */
    default Optional<ModelTrainingStatus> findDefaultModelStatus() {
        return findByModelName(ModelTrainingStatus.DEFAULT_MODEL_NAME);
    }

    /**
     * Check if a model exists.
     */
    boolean existsByModelName(String modelName);

    /**
     * Find models in a specific phase.
     */
    Optional<ModelTrainingStatus> findByCurrentPhase(String phase);

    /**
     * Update accuracy metrics atomically.
     */
    @Modifying
    @Query("UPDATE ModelTrainingStatus m SET " +
           "m.currentAccuracy = :accuracy, " +
           "m.currentPrecision = :precision, " +
           "m.currentRecall = :recall, " +
           "m.currentF1 = :f1, " +
           "m.lastEvaluationDate = CURRENT_TIMESTAMP, " +
           "m.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE m.modelName = :modelName")
    int updateAccuracyMetrics(@Param("modelName") String modelName,
                              @Param("accuracy") BigDecimal accuracy,
                              @Param("precision") BigDecimal precision,
                              @Param("recall") BigDecimal recall,
                              @Param("f1") BigDecimal f1);

    /**
     * Update phase atomically.
     */
    @Modifying
    @Query("UPDATE ModelTrainingStatus m SET " +
           "m.currentPhase = :phase, " +
           "m.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE m.modelName = :modelName")
    int updatePhase(@Param("modelName") String modelName, @Param("phase") String phase);

    /**
     * Increment LLM calls total.
     */
    @Modifying
    @Query("UPDATE ModelTrainingStatus m SET " +
           "m.llmCallsTotal = m.llmCallsTotal + 1, " +
           "m.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE m.modelName = :modelName")
    int incrementLlmCalls(@Param("modelName") String modelName);

    /**
     * Increment saved LLM calls.
     */
    @Modifying
    @Query("UPDATE ModelTrainingStatus m SET " +
           "m.llmCallsSaved = m.llmCallsSaved + 1, " +
           "m.estimatedTokensSaved = m.estimatedTokensSaved + :tokens, " +
           "m.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE m.modelName = :modelName")
    int incrementSavedLlmCalls(@Param("modelName") String modelName, @Param("tokens") long tokens);

    /**
     * Increment training samples from LLM.
     */
    @Modifying
    @Query("UPDATE ModelTrainingStatus m SET " +
           "m.samplesFromLlm = m.samplesFromLlm + 1, " +
           "m.totalTrainingSamples = m.totalTrainingSamples + 1, " +
           "m.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE m.modelName = :modelName")
    int incrementLlmSamples(@Param("modelName") String modelName);

    /**
     * Increment training samples from feedback.
     */
    @Modifying
    @Query("UPDATE ModelTrainingStatus m SET " +
           "m.samplesFromFeedback = m.samplesFromFeedback + 1, " +
           "m.totalTrainingSamples = m.totalTrainingSamples + 1, " +
           "m.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE m.modelName = :modelName")
    int incrementFeedbackSamples(@Param("modelName") String modelName);

    /**
     * Get or create default model status.
     */
    default ModelTrainingStatus getOrCreateDefault() {
        return findDefaultModelStatus().orElseGet(() -> {
            ModelTrainingStatus status = ModelTrainingStatus.builder()
                    .modelName(ModelTrainingStatus.DEFAULT_MODEL_NAME)
                    .currentPhase(ModelTrainingStatus.PHASE_BOOTSTRAP)
                    .build();
            return save(status);
        });
    }
}
