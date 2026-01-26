package com.agentframework.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class RunAgentRequest {
    private String query;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("max_iterations")
    private Integer maxIterations = 10;

    @JsonProperty("auto_approve")
    private Boolean autoApprove = false;

    /**
     * If true, clears the session after this request completes.
     * Use this for the last message in a conversation.
     */
    @JsonProperty("is_last_session")
    private Boolean isLastSession = false;
}
