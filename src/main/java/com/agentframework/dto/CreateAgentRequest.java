package com.agentframework.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateAgentRequest {
    
    @NotBlank(message = "Agent name is required")
    private String name;
    
    @NotBlank(message = "Agent description is required")
    private String description;
}

