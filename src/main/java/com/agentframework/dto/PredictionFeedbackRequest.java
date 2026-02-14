package com.agentframework.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for providing feedback on a tool prediction.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionFeedbackRequest {

    /**
     * ID of the prediction this feedback is for.
     */
    @NotBlank(message = "Prediction ID is required")
    @JsonProperty("prediction_id")
    private String predictionId;

    /**
     * Overall feedback type.
     * Options: 'correct', 'partial', 'incorrect', 'not_used'
     */
    @NotBlank(message = "Feedback type is required")
    @JsonProperty("feedback_type")
    private String feedbackType;

    /**
     * Optional: Overall accuracy rating (1-5 stars).
     * 5 = Perfect prediction
     * 4 = Good, minor issues
     * 3 = Acceptable, some tools missing/wrong
     * 2 = Poor, many tools missing/wrong
     * 1 = Completely wrong
     */
    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    @JsonProperty("accuracy_rating")
    private Integer accuracyRating;

    /**
     * Optional: List of tools from the prediction that were correct.
     */
    @JsonProperty("correct_tools")
    private List<String> correctTools;

    /**
     * Optional: List of tools that should have been predicted but weren't.
     */
    @JsonProperty("missing_tools")
    private List<String> missingTools;

    /**
     * Optional: List of tools that were predicted but shouldn't have been.
     */
    @JsonProperty("incorrect_tools")
    private List<String> incorrectTools;

    /**
     * Optional: Free-form user comment about the prediction.
     */
    @JsonProperty("user_comment")
    private String userComment;

    /**
     * Optional: User ID of who is providing feedback.
     */
    @JsonProperty("user_id")
    private String userId;
}
