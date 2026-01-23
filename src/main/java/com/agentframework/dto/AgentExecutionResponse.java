package com.agentframework.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AgentExecutionResponse {
    
    @JsonProperty("agent_name")
    private String agentName;
    
    private String status;
    
    private String result;
    
    @JsonProperty("tools_used")
    private List<ToolExecutionResult> toolsUsed;
    
    @JsonProperty("execution_time_ms")
    private long executionTimeMs;
    
    private String error;
    
    @Data
    @Builder
    public static class ToolExecutionResult {
        @JsonProperty("tool_name")
        private String toolName;
        
        private String status;
        
        private Object output;
        
        @JsonProperty("execution_time_ms")
        private long executionTimeMs;
    }
}

