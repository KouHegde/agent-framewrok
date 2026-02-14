package com.agentframework.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing aggregated learning statistics for a tool.
 * Used for online learning and confidence calculation.
 */
@Entity
@Table(name = "tool_learning_stats")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolLearningStat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Tool name (unique).
     */
    @Column(name = "tool_name", nullable = false, unique = true)
    private String toolName;

    /**
     * Total times this tool was predicted.
     */
    @Column(name = "total_predictions")
    @Builder.Default
    private Integer totalPredictions = 0;

    /**
     * Times it was marked correct.
     */
    @Column(name = "correct_predictions")
    @Builder.Default
    private Integer correctPredictions = 0;

    /**
     * Times it was marked incorrect.
     */
    @Column(name = "incorrect_predictions")
    @Builder.Default
    private Integer incorrectPredictions = 0;

    /**
     * Times it should have been predicted but wasn't.
     */
    @Column(name = "missed_predictions")
    @Builder.Default
    private Integer missedPredictions = 0;

    /**
     * Precision = correct / (correct + incorrect).
     */
    @Column(name = "precision_score", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal precisionScore = BigDecimal.ZERO;

    /**
     * Recall = correct / (correct + missed).
     */
    @Column(name = "recall_score", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal recallScore = BigDecimal.ZERO;

    /**
     * F1 = 2 * (precision * recall) / (precision + recall).
     */
    @Column(name = "f1_score", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal f1Score = BigDecimal.ZERO;

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

    /**
     * Increment correct prediction count and recalculate metrics.
     */
    public void recordCorrectPrediction() {
        this.totalPredictions++;
        this.correctPredictions++;
        recalculateMetrics();
    }

    /**
     * Increment incorrect prediction count and recalculate metrics.
     */
    public void recordIncorrectPrediction() {
        this.totalPredictions++;
        this.incorrectPredictions++;
        recalculateMetrics();
    }

    /**
     * Increment missed prediction count and recalculate metrics.
     */
    public void recordMissedPrediction() {
        this.missedPredictions++;
        recalculateMetrics();
    }

    /**
     * Recalculate precision, recall, and F1 scores.
     */
    public void recalculateMetrics() {
        // Precision = correct / (correct + incorrect)
        int precisionDenom = correctPredictions + incorrectPredictions;
        if (precisionDenom > 0) {
            this.precisionScore = BigDecimal.valueOf(correctPredictions)
                    .divide(BigDecimal.valueOf(precisionDenom), 4, RoundingMode.HALF_UP);
        } else {
            this.precisionScore = BigDecimal.ZERO;
        }

        // Recall = correct / (correct + missed)
        int recallDenom = correctPredictions + missedPredictions;
        if (recallDenom > 0) {
            this.recallScore = BigDecimal.valueOf(correctPredictions)
                    .divide(BigDecimal.valueOf(recallDenom), 4, RoundingMode.HALF_UP);
        } else {
            this.recallScore = BigDecimal.ZERO;
        }

        // F1 = 2 * (precision * recall) / (precision + recall)
        BigDecimal sum = precisionScore.add(recallScore);
        if (sum.compareTo(BigDecimal.ZERO) > 0) {
            this.f1Score = precisionScore.multiply(recallScore)
                    .multiply(BigDecimal.valueOf(2))
                    .divide(sum, 4, RoundingMode.HALF_UP);
        } else {
            this.f1Score = BigDecimal.ZERO;
        }
    }

    /**
     * Get success rate as a simple percentage.
     */
    public double getSuccessRate() {
        if (totalPredictions == 0) {
            return 0.0;
        }
        return (double) correctPredictions / totalPredictions;
    }
}
