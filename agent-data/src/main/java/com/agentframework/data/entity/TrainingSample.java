package com.agentframework.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing a training sample for the ML classifier.
 * Stores query-tool pairs for batch training.
 */
@Entity
@Table(name = "training_samples")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingSample {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * User query.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String query;

    /**
     * Query embedding for similarity search.
     * Stored as a float array and mapped to pgvector's vector type.
     * Using 384 dimensions (MiniLM model).
     */
    @Column(name = "query_embedding", columnDefinition = "vector(384)")
    private float[] queryEmbedding;

    /**
     * Correct tools for this query.
     */
    @Column(nullable = false, columnDefinition = "TEXT[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] tools;

    /**
     * Source of this sample.
     * Options: 'user_feedback', 'manual', 'synthetic'
     */
    @Column(nullable = false)
    private String source;

    /**
     * Link to original prediction if from feedback.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prediction_id")
    private ToolPrediction prediction;

    /**
     * How confident are we in this sample (0-1).
     * Higher confidence samples get more weight in training.
     */
    @Column(name = "confidence_level", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal confidenceLevel = BigDecimal.ONE;

    /**
     * Has this been manually verified.
     */
    @Column
    @Builder.Default
    private Boolean verified = false;

    /**
     * Has this been used in a training run.
     */
    @Column(name = "used_in_training")
    @Builder.Default
    private Boolean usedInTraining = false;

    /**
     * ID of the last training run that used this.
     */
    @Column(name = "last_training_run")
    private String lastTrainingRun;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * Convert tools array to list.
     */
    public List<String> getToolsList() {
        return tools != null ? List.of(tools) : List.of();
    }

    /**
     * Set tools from list.
     */
    public void setToolsFromList(List<String> toolsList) {
        this.tools = toolsList != null ? toolsList.toArray(new String[0]) : new String[0];
    }

    /**
     * Mark this sample as used in training.
     */
    public void markUsedInTraining(String trainingRunId) {
        this.usedInTraining = true;
        this.lastTrainingRun = trainingRunId;
    }

    /**
     * Create a training sample from feedback.
     */
    public static TrainingSample fromFeedback(ToolPrediction prediction, List<String> correctTools, 
                                               BigDecimal confidence) {
        return TrainingSample.builder()
                .query(prediction.getQuery())
                .queryEmbedding(prediction.getQueryEmbedding())
                .tools(correctTools.toArray(new String[0]))
                .source("user_feedback")
                .prediction(prediction)
                .confidenceLevel(confidence)
                .verified(false)
                .usedInTraining(false)
                .build();
    }
}
