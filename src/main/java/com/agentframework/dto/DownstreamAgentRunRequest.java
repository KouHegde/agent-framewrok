package com.agentframework.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DownstreamAgentRunRequest {
    @JsonProperty("agent_id")
    private String agentId;

    private String query;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("max_iterations")
    private Integer maxIterations;

    @JsonProperty("auto_approve")
    private Boolean autoApprove;
}
