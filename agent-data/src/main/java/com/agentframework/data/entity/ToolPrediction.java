package com.agentframework.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Entity representing a tool prediction made by the system.
 * Stores predictions for analysis, feedback collection, and ML training.
 */
@Entity
@Table(name = "tool_predictions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * User query that triggered prediction.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String query;

    /**
     * Query embedding for similarity search.
     * Stored as a float array and mapped to pgvector's vector type in SQL.
     * Using 384 dimensions (MiniLM model).
     */
    @Column(name = "query_embedding", columnDefinition = "vector(384)")
    private float[] queryEmbedding;

    /**
     * Array of predicted tool names.
     */
    @Column(name = "predicted_tools", columnDefinition = "TEXT[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] predictedTools;

    /**
     * Confidence scores per tool as JSON.
     * Format: {"tool_name": 0.95, ...}
     */
    @Column(name = "confidence_scores", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Double> confidenceScores;

    /**
     * Method used for prediction.
     * Options: 'keyword', 'llm', 'cosine', 'ml_classifier', 'hybrid'
     */
    @Column(name = "prediction_method", nullable = false)
    private String predictionMethod;

    /**
     * Human-readable explanation.
     */
    @Column(columnDefinition = "TEXT")
    private String reasoning;

    /**
     * Version of ML model used (if applicable).
     */
    @Column(name = "model_version")
    private String modelVersion;

    /**
     * Name of the model used.
     */
    @Column(name = "model_name")
    private String modelName;

    /**
     * User who made the query.
     */
    @Column(name = "user_id")
    private String userId;

    /**
     * Organization/workspace.
     */
    @Column(name = "tenant_id")
    private String tenantId;

    /**
     * Session identifier for grouping.
     */
    @Column(name = "session_id")
    private String sessionId;

    /**
     * Status of the prediction.
     * Options: 'pending', 'completed', 'used', 'discarded'
     */
    @Column
    @Builder.Default
    private String status = "pending";

    /**
     * Time taken for prediction in milliseconds.
     */
    @Column(name = "prediction_time_ms")
    private Integer predictionTimeMs;

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
     * Convert predicted tools array to list.
     */
    public List<String> getPredictedToolsList() {
        return predictedTools != null ? List.of(predictedTools) : List.of();
    }

    /**
     * Set predicted tools from list.
     */
    public void setPredictedToolsFromList(List<String> tools) {
        this.predictedTools = tools != null ? tools.toArray(new String[0]) : new String[0];
    }
}
