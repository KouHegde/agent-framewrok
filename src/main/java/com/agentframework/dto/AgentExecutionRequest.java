package com.agentframework.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class AgentExecutionRequest {
    
    @NotNull(message = "Agent spec is required")
    private AgentSpec agentSpec;
    
    @NotBlank(message = "User query is required")
    private String query;
    
    /**
     * Dynamic inputs provided by the user at runtime
     * e.g., {"project_key": "PAYMENT", "time_range": "last 7 days"}
     */
    private Map<String, Object> inputs;
}

