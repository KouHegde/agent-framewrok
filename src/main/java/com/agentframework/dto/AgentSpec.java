package com.agentframework.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AgentSpec {
    
    @JsonProperty("agent_name")
    private String agentName;
    
    private String goal;
    
    @JsonProperty("allowed_tools")
    private List<String> allowedTools;
    
    @JsonProperty("execution_mode")
    private String executionMode;
    
    private List<String> permissions;
    
    @JsonProperty("expected_inputs")
    private List<String> expectedInputs;
}

