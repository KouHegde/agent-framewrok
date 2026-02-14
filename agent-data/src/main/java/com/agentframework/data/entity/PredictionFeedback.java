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
import java.util.UUID;

/**
 * Entity representing user feedback on a tool prediction.
 * Used for learning and improving prediction accuracy.
 */
@Entity
@Table(name = "prediction_feedback")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Reference to the prediction this feedback is for.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prediction_id", nullable = false)
    private ToolPrediction prediction;

    /**
     * Overall feedback type.
     * Options: 'correct', 'partial', 'incorrect', 'not_used'
     */
    @Column(name = "feedback_type", nullable = false)
    private String feedbackType;

    /**
     * Overall accuracy rating (1-5 stars).
     */
    @Column(name = "accuracy_rating")
    private Integer accuracyRating;

    /**
     * Tools from the prediction that were correct.
     */
    @Column(name = "correct_tools", columnDefinition = "TEXT[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] correctTools;

    /**
     * Tools that should have been predicted but weren't.
     */
    @Column(name = "missing_tools", columnDefinition = "TEXT[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] missingTools;

    /**
     * Tools that were predicted but shouldn't have been.
     */
    @Column(name = "incorrect_tools", columnDefinition = "TEXT[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] incorrectTools;

    /**
     * Optional user comment.
     */
    @Column(name = "user_comment", columnDefinition = "TEXT")
    private String userComment;

    /**
     * User who provided feedback.
     */
    @Column(name = "user_id")
    private String userId;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * Convert arrays to lists for convenience.
     */
    public List<String> getCorrectToolsList() {
        return correctTools != null ? List.of(correctTools) : List.of();
    }

    public List<String> getMissingToolsList() {
        return missingTools != null ? List.of(missingTools) : List.of();
    }

    public List<String> getIncorrectToolsList() {
        return incorrectTools != null ? List.of(incorrectTools) : List.of();
    }

    public void setCorrectToolsFromList(List<String> tools) {
        this.correctTools = tools != null ? tools.toArray(new String[0]) : new String[0];
    }

    public void setMissingToolsFromList(List<String> tools) {
        this.missingTools = tools != null ? tools.toArray(new String[0]) : new String[0];
    }

    public void setIncorrectToolsFromList(List<String> tools) {
        this.incorrectTools = tools != null ? tools.toArray(new String[0]) : new String[0];
    }
}
