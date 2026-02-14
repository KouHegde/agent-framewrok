package com.agentframework.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for predicting which MCP tools are needed for a query.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolPredictionRequest {

    /**
     * The user query to analyze for tool selection.
     */
    @NotBlank(message = "Query is required")
    private String query;

    /**
     * Optional: Additional context about what the user wants to achieve.
     */
    private String context;

    /**
     * Optional: User ID for personalized predictions.
     */
    @JsonProperty("user_id")
    private String userId;

    /**
     * Optional: Tenant/organization ID.
     */
    @JsonProperty("tenant_id")
    private String tenantId;

    /**
     * Optional: Session ID for grouping related predictions.
     */
    @JsonProperty("session_id")
    private String sessionId;

    /**
     * Optional: Maximum number of tools to return.
     * Default is 3 if not specified.
     */
    @JsonProperty("max_tools")
    @Builder.Default
    private Integer maxTools = 3;

    /**
     * Optional: Minimum confidence threshold (0.0 - 1.0).
     * Tools below this threshold won't be included.
     * Default is 0.5 if not specified.
     */
    @JsonProperty("min_confidence")
    @Builder.Default
    private Double minConfidence = 0.5;

    /**
     * Optional: Force a specific prediction method.
     * If not set, the system will use the best available method.
     * Options: 'keyword', 'llm', 'cosine', 'ml_classifier', 'hybrid'
     */
    @JsonProperty("force_method")
    private String forceMethod;

    /**
     * Optional: Include detailed reasoning in response.
     */
    @JsonProperty("include_reasoning")
    @Builder.Default
    private Boolean includeReasoning = true;
}
