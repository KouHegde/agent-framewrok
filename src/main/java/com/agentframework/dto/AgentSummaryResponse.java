package com.agentframework.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentSummaryResponse {
    private String agentId;
    private String name;
    private String description;
    private List<String> mcpServers;
    private String createdAt;
    private String downstreamStatus;
    private String downstreamAgentId;
    private DownstreamAgentDetailResponse downstream;
}
