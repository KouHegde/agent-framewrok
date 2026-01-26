package com.agentframework.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DownstreamAgentRunResponse {
    @JsonProperty("agent_id")
    private String agentId;
    private String status;
    private String answer;
    @JsonProperty("session_id")
    private String sessionId;
    private Map<String, Object> result;
    private String error;
}
