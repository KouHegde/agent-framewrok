package com.agentframework.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
public class RunAgentRequest {
    private String query;

    @JsonProperty("user_id")
    private String userId;

    private Map<String, Object> context;

    @JsonProperty("session_id")
    private String sessionId;
}
