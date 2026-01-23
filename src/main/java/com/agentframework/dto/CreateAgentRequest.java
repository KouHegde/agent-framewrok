package com.agentframework.dto;

import lombok.Data;

@Data
public class CreateAgentRequest {
    
    private String userId;
    
    private String name;
    
    private String description;
}
