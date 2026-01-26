package com.agentframework.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentDetailResponse {
    private String agentId;
    private String name;
    private String description;
    private String agentSpec;
    private List<String> mcpServers;
    private String createdAt;
    private String updatedAt;
}
