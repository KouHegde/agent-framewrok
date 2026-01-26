package com.agentframework.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CreateAgentRequest {
    
    private String name;
    
    private String description;
    
    @JsonProperty("owner_id")
    private String ownerId;
    
    @JsonProperty("tenant_id")
    private String tenantId;
    
    // Legacy field (deprecated, use ownerId)
    private String userId;
    
    // Convenience method
    public String getOwnerId() {
        return ownerId != null ? ownerId : userId;
    }
}
