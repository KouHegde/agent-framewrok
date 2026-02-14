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
 * Entity representing learned weights for keyword-tool associations.
 * Used for online learning in the keyword-based selection strategy.
 */
@Entity
@Table(name = "keyword_tool_weights",
        uniqueConstraints = @UniqueConstraint(columnNames = {"keyword", "tool_name"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeywordToolWeight {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The keyword.
     */
    @Column(nullable = false)
    private String keyword;

    /**
     * The tool name.
     */
    @Column(name = "tool_name", nullable = false)
    private String toolName;

    /**
     * Association strength (higher = stronger).
     * Starts at 1.0 and is adjusted based on feedback.
     */
    @Column(precision = 8, scale = 6)
    @Builder.Default
    private BigDecimal weight = BigDecimal.ONE;

    /**
     * How many times this association was seen.
     */
    @Column(name = "occurrence_count")
    @Builder.Default
    private Integer occurrenceCount = 0;

    /**
     * How many times this led to correct prediction.
     */
    @Column(name = "success_count")
    @Builder.Default
    private Integer successCount = 0;

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
     * Record a successful use of this keyword-tool association.
     * Increases weight using exponential moving average.
     */
    public void recordSuccess() {
        this.occurrenceCount++;
        this.successCount++;
        updateWeight();
    }

    /**
     * Record a failed use of this keyword-tool association.
     * Decreases weight.
     */
    public void recordFailure() {
        this.occurrenceCount++;
        updateWeight();
    }

    /**
     * Update weight based on success rate using exponential moving average.
     * Weight = base_weight * success_rate + smoothing_factor
     */
    private void updateWeight() {
        if (occurrenceCount == 0) {
            this.weight = BigDecimal.ONE;
            return;
        }

        // Success rate
        BigDecimal successRate = BigDecimal.valueOf(successCount)
                .divide(BigDecimal.valueOf(occurrenceCount), 6, RoundingMode.HALF_UP);

        // Weight formula: 0.5 + (success_rate * 1.5)
        // This gives range of 0.5 (all failures) to 2.0 (all successes)
        BigDecimal baseWeight = BigDecimal.valueOf(0.5);
        BigDecimal multiplier = BigDecimal.valueOf(1.5);

        this.weight = baseWeight.add(successRate.multiply(multiplier))
                .setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * Get the current success rate.
     */
    public double getSuccessRate() {
        if (occurrenceCount == 0) {
            return 0.5; // Default neutral rate
        }
        return (double) successCount / occurrenceCount;
    }
}
