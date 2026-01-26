package com.agentframework.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DownstreamAgentRunRequest {
    @JsonProperty("agent_id")
    private String agentId;
    private String query;
    @JsonProperty("user_id")
    private String userId;
    private Map<String, Object> context;
    @JsonProperty("session_id")
    private String sessionId;
}
