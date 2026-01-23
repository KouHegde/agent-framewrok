package com.agentframework.dto;

import lombok.Data;

import java.util.Map;

@Data
public class AgentExecutionRequest {
    
    private AgentSpec agentSpec;
    
    private String query;
    
    /**
     * Dynamic inputs provided by the user at runtime
     * e.g., {"project_key": "PAYMENT", "time_range": "last 7 days"}
     */
    private Map<String, Object> inputs;
}
