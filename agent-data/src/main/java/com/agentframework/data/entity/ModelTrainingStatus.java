package com.agentframework.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity tracking ML model training status and phases.
 * 
 * Training Phases:
 * - bootstrap: Using LLM for predictions, collecting training data
 * - training: Model is being trained
 * - hybrid: ML + LLM fallback (accuracy 80-90%)
 * - ml_primary: ML primary, LLM only for edge cases (accuracy > 90%)
 */
@Entity
@Table(name = "model_training_status")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelTrainingStatus {

    public static final String PHASE_BOOTSTRAP = "bootstrap";
    public static final String PHASE_TRAINING = "training";
    public static final String PHASE_HYBRID = "hybrid";
    public static final String PHASE_ML_PRIMARY = "ml_primary";

    public static final String DEFAULT_MODEL_NAME = "tool-selector";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Model name (unique identifier).
     */
    @Column(name = "model_name", nullable = false, unique = true)
    @Builder.Default
    private String modelName = DEFAULT_MODEL_NAME;

    /**
     * Current training phase.
     */
    @Column(name = "current_phase", nullable = false)
    @Builder.Default
    private String currentPhase = PHASE_BOOTSTRAP;

    // Training Metrics
    @Column(name = "total_training_samples")
    @Builder.Default
    private Integer totalTrainingSamples = 0;

    @Column(name = "samples_from_llm")
    @Builder.Default
    private Integer samplesFromLlm = 0;

    @Column(name = "samples_from_feedback")
    @Builder.Default
    private Integer samplesFromFeedback = 0;

    // Accuracy Metrics
    @Column(name = "current_accuracy", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal currentAccuracy = BigDecimal.ZERO;

    @Column(name = "current_precision", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal currentPrecision = BigDecimal.ZERO;

    @Column(name = "current_recall", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal currentRecall = BigDecimal.ZERO;

    @Column(name = "current_f1", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal currentF1 = BigDecimal.ZERO;

    // Thresholds
    @Column(name = "hybrid_threshold", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal hybridThreshold = new BigDecimal("0.80");

    @Column(name = "ml_primary_threshold", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal mlPrimaryThreshold = new BigDecimal("0.90");

    @Column(name = "min_confidence_for_ml", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal minConfidenceForMl = new BigDecimal("0.75");

    // Training History
    @Column(name = "last_training_run")
    private String lastTrainingRun;

    @Column(name = "last_training_date")
    private Instant lastTrainingDate;

    @Column(name = "last_evaluation_date")
    private Instant lastEvaluationDate;

    @Column(name = "training_runs_count")
    @Builder.Default
    private Integer trainingRunsCount = 0;

    // LLM Token Savings
    @Column(name = "llm_calls_total")
    @Builder.Default
    private Integer llmCallsTotal = 0;

    @Column(name = "llm_calls_saved")
    @Builder.Default
    private Integer llmCallsSaved = 0;

    @Column(name = "estimated_tokens_saved")
    @Builder.Default
    private Long estimatedTokensSaved = 0L;

    @Column(name = "active_model_version")
    private String activeModelVersion;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // =====================================================
    // Phase Checking Methods
    // =====================================================

    /**
     * Check if we're in bootstrap phase (no trained model, using LLM).
     */
    public boolean isBootstrapPhase() {
        return PHASE_BOOTSTRAP.equals(currentPhase);
    }

    /**
     * Check if we're in training phase.
     */
    public boolean isTrainingPhase() {
        return PHASE_TRAINING.equals(currentPhase);
    }

    /**
     * Check if we're in hybrid phase (ML + LLM fallback).
     */
    public boolean isHybridPhase() {
        return PHASE_HYBRID.equals(currentPhase);
    }

    /**
     * Check if we're in ML primary phase.
     */
    public boolean isMlPrimaryPhase() {
        return PHASE_ML_PRIMARY.equals(currentPhase);
    }

    /**
     * Check if ML model should be used (based on phase and accuracy).
     */
    public boolean shouldUseMlModel() {
        return isHybridPhase() || isMlPrimaryPhase();
    }

    /**
     * Check if LLM should be used as primary.
     */
    public boolean shouldUseLlmAsPrimary() {
        return isBootstrapPhase() || isTrainingPhase();
    }

    // =====================================================
    // Metric Update Methods
    // =====================================================

    /**
     * Record an LLM call made for training data collection.
     */
    public void recordLlmCall() {
        this.llmCallsTotal++;
    }

    /**
     * Record an LLM call that was saved by using ML.
     */
    public void recordSavedLlmCall(int estimatedTokens) {
        this.llmCallsSaved++;
        this.estimatedTokensSaved += estimatedTokens;
    }

    /**
     * Increment LLM training samples count.
     */
    public void incrementLlmSamples() {
        this.samplesFromLlm++;
        this.totalTrainingSamples++;
    }

    /**
     * Increment feedback training samples count.
     */
    public void incrementFeedbackSamples() {
        this.samplesFromFeedback++;
        this.totalTrainingSamples++;
    }

    /**
     * Update accuracy metrics after evaluation.
     */
    public void updateAccuracyMetrics(BigDecimal accuracy, BigDecimal precision, 
                                       BigDecimal recall, BigDecimal f1) {
        this.currentAccuracy = accuracy;
        this.currentPrecision = precision;
        this.currentRecall = recall;
        this.currentF1 = f1;
        this.lastEvaluationDate = Instant.now();
        
        // Auto-transition phase based on accuracy
        updatePhaseBasedOnAccuracy();
    }

    /**
     * Update phase based on current accuracy.
     */
    public void updatePhaseBasedOnAccuracy() {
        if (currentAccuracy.compareTo(mlPrimaryThreshold) >= 0) {
            this.currentPhase = PHASE_ML_PRIMARY;
        } else if (currentAccuracy.compareTo(hybridThreshold) >= 0) {
            this.currentPhase = PHASE_HYBRID;
        }
        // Stay in bootstrap if accuracy < hybrid threshold
    }

    /**
     * Record a training run.
     */
    public void recordTrainingRun(String runId, String modelVersion) {
        this.lastTrainingRun = runId;
        this.lastTrainingDate = Instant.now();
        this.trainingRunsCount++;
        this.activeModelVersion = modelVersion;
    }

    /**
     * Get LLM call savings percentage.
     */
    public double getLlmSavingsPercentage() {
        if (llmCallsTotal == 0) {
            return 0.0;
        }
        return (double) llmCallsSaved / (llmCallsTotal + llmCallsSaved) * 100;
    }

    /**
     * Check if we have enough samples to start training.
     */
    public boolean hasEnoughSamplesForTraining(int minSamples) {
        return totalTrainingSamples >= minSamples;
    }
}
