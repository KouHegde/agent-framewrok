package com.agentframework.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO after submitting prediction feedback.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionFeedbackResponse {

    /**
     * Unique identifier for this feedback record.
     */
    @JsonProperty("feedback_id")
    private String feedbackId;

    /**
     * The prediction ID this feedback was for.
     */
    @JsonProperty("prediction_id")
    private String predictionId;

    /**
     * Status of the feedback submission.
     */
    private String status;

    /**
     * Human-readable message.
     */
    private String message;

    /**
     * Whether this feedback triggered immediate learning updates.
     */
    @JsonProperty("learning_updated")
    private Boolean learningUpdated;

    /**
     * Whether a new training sample was created from this feedback.
     */
    @JsonProperty("training_sample_created")
    private Boolean trainingSampleCreated;

    /**
     * When this feedback was received.
     */
    @JsonProperty("created_at")
    private Instant createdAt;

    /**
     * Create a success response.
     */
    public static PredictionFeedbackResponse success(String feedbackId, String predictionId, 
                                                      boolean learningUpdated, boolean trainingSampleCreated) {
        return PredictionFeedbackResponse.builder()
                .feedbackId(feedbackId)
                .predictionId(predictionId)
                .status("accepted")
                .message("Feedback recorded successfully. Thank you for helping improve our predictions!")
                .learningUpdated(learningUpdated)
                .trainingSampleCreated(trainingSampleCreated)
                .createdAt(Instant.now())
                .build();
    }

    /**
     * Create an error response.
     */
    public static PredictionFeedbackResponse error(String predictionId, String errorMessage) {
        return PredictionFeedbackResponse.builder()
                .predictionId(predictionId)
                .status("error")
                .message(errorMessage)
                .learningUpdated(false)
                .trainingSampleCreated(false)
                .createdAt(Instant.now())
                .build();
    }
}
