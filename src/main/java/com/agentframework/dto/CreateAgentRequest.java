package com.agentframework.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Request DTO for creating a new agent.
 */
@Data
public class CreateAgentRequest {
    
    /**
     * Display name for the agent.
     */
    private String name;
    
    /**
     * Description of what the agent should do.
     * Used for automatic tool selection if selectedTools is not provided.
     */
    private String description;
    
    @JsonProperty("owner_id")
    private String ownerId;
    
    @JsonProperty("tenant_id")
    private String tenantId;
    
    // Legacy field (deprecated, use ownerId)
    private String userId;
    
    /**
     * List of MCP tools selected by the user from the UI.
     * If provided, these tools will be used directly instead of automatic selection.
     * If empty or null, automatic tool selection (LLM/keyword-based) will be used as fallback.
     * 
     * Example: ["mcp_jira_call_jira_rest_api", "mcp_confluence_search_confluence_pages"]
     */
    @JsonProperty("selected_tools")
    private List<String> selectedTools;
    
    /**
     * Optional: Prediction ID if the tools were selected from a prediction.
     * Used to link the agent creation to the prediction for feedback/learning.
     */
    @JsonProperty("prediction_id")
    private String predictionId;
    
    /**
     * Optional: Skip automatic tool selection even if selectedTools is empty.
     * Default is false (auto-selection is used as fallback).
     */
    @JsonProperty("skip_auto_selection")
    private Boolean skipAutoSelection;
    
    // Convenience method
    public String getOwnerId() {
        return ownerId != null ? ownerId : userId;
    }
    
    /**
     * Check if tools were explicitly selected by the user.
     */
    public boolean hasSelectedTools() {
        return selectedTools != null && !selectedTools.isEmpty();
    }
    
    /**
     * Check if auto-selection should be used.
     */
    public boolean shouldUseAutoSelection() {
        return !hasSelectedTools() && (skipAutoSelection == null || !skipAutoSelection);
    }
}
