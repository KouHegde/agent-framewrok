package com.agentframework.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentCreateResponse {
    private String agentId;
    private String name;
    private String description;
    private String status;
    private String message;
    private List<String> allowedTools;
    private List<String> mcpServers;
    private String createdAt;
    private String runEndpoint;
    private AgentRunExample runExample;
    private AgentSpec agentSpec;
    private Map<String, String> dataFetchStatus;
    private String dataFetchMessage;
    private String downstreamStatus;
    private String downstreamAgentId;
}
