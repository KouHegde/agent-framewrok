package com.agentframework.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for tool prediction results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolPredictionResponse {

    /**
     * Unique identifier for this prediction (for feedback reference).
     */
    @JsonProperty("prediction_id")
    private String predictionId;

    /**
     * The original query that was analyzed.
     */
    private String query;

    /**
     * List of predicted tools with their details.
     */
    @JsonProperty("predicted_tools")
    private List<PredictedTool> predictedTools;

    /**
     * Overall confidence of the prediction (0.0 - 1.0).
     */
    private Double confidence;

    /**
     * Method used for prediction.
     * Options: 'keyword', 'llm', 'cosine', 'ml_classifier', 'hybrid'
     */
    @JsonProperty("prediction_method")
    private String predictionMethod;

    /**
     * Human-readable explanation of why these tools were selected.
     */
    private String reasoning;

    /**
     * Time taken for prediction in milliseconds.
     */
    @JsonProperty("prediction_time_ms")
    private Integer predictionTimeMs;

    /**
     * When this prediction was made.
     */
    @JsonProperty("created_at")
    private Instant createdAt;

    /**
     * Feedback URL for providing feedback on this prediction.
     */
    @JsonProperty("feedback_url")
    private String feedbackUrl;

    /**
     * Represents a single predicted tool with its confidence and reasoning.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictedTool {
        /**
         * Tool name (e.g., 'mcp_jira_call_jira_rest_api').
         */
        private String name;

        /**
         * Tool category (e.g., 'jira', 'confluence', 'github', 'webex').
         */
        private String category;

        /**
         * Human-readable description of the tool.
         */
        private String description;

        /**
         * Confidence score for this specific tool (0.0 - 1.0).
         */
        private Double confidence;

        /**
         * Why this tool was selected (tool-specific reasoning).
         */
        private String reason;

        /**
         * Historical success rate for this tool (from learning data).
         */
        @JsonProperty("success_rate")
        private Double successRate;

        /**
         * Required inputs for this tool.
         */
        @JsonProperty("required_inputs")
        private List<String> requiredInputs;

        /**
         * Tool capabilities.
         */
        private List<String> capabilities;
    }

    /**
     * Create an error response.
     */
    public static ToolPredictionResponse error(String query, String errorMessage) {
        return ToolPredictionResponse.builder()
                .query(query)
                .reasoning("Error: " + errorMessage)
                .confidence(0.0)
                .predictedTools(List.of())
                .createdAt(Instant.now())
                .build();
    }
}
