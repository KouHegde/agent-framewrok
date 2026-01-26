package com.agentframework.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DownstreamToolDetailResponse {
    private String id;
    @JsonProperty("tool_name")
    private String toolName;
    @JsonProperty("mcp_server_id")
    private String mcpServerId;
    @JsonProperty("tool_description")
    private String toolDescription;
    @JsonProperty("input_schema")
    private Map<String, Object> inputSchema;
    @JsonProperty("is_enabled")
    private Boolean isEnabled;
    @JsonProperty("requires_approval")
    private Boolean requiresApproval;
}
