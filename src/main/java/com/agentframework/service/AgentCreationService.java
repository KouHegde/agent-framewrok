package com.agentframework.service;

import com.agentframework.common.dto.AgentDto;
import com.agentframework.common.dto.AgentConfigDto;
import com.agentframework.data.facade.AgentDataFacade;
import com.agentframework.dto.AgentCreateResponse;
import com.agentframework.dto.AgentCreationOutcome;
import com.agentframework.dto.AgentRunExample;
import com.agentframework.dto.AgentSpec;
import com.agentframework.dto.CreateAgentRequest;
import com.agentframework.registry.MCPTool;
import com.agentframework.registry.MCPToolRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentCreationService {

    private final MetaAgentService metaAgentService;
    private final MCPToolRegistry toolRegistry;
    private final MCPDataFetcherService mcpDataFetcherService;
    private final ConfigDeciderService configDeciderService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private AgentDataFacade agentDataFacade;

    public AgentCreationOutcome createAgent(CreateAgentRequest request) {
        String ownerId = request.getOwnerId();
        log.info("Creating agent: {} for user: {}", request.getName(), ownerId);

        AgentSpec agentSpec = buildAgentSpec(request);
        List<String> normalizedTools = normalizeTools(agentSpec.getAllowedTools());
        String allowedToolsKey = String.join(",", normalizedTools);

        ensureDatabaseConfigured();

        AgentCreationOutcome existing = handleExistingAgent(allowedToolsKey);
        if (existing != null) {
            return existing;
        }

        AgentConfigDto config = decideConfig(agentSpec, request);
        applyConfigToSpec(agentSpec, config);
        AgentDto agent = persistNewAgent(request, agentSpec, allowedToolsKey, ownerId, config);
        log.info("Agent created: {}", agent.id());
        AgentCreateResponse response = buildFullResponse(agent, agentSpec, normalizedTools);
        return new AgentCreationOutcome(response, true);
    }

    private AgentSpec buildAgentSpec(CreateAgentRequest request) {
        AgentSpec agentSpec = metaAgentService.buildAgentSpec(request);
        log.info("Selected MCP tools: {}", agentSpec.getAllowedTools());
        toolRegistry.ensureToolsPersisted(agentSpec.getAllowedTools());
        return agentSpec;
    }

    private List<String> normalizeTools(List<String> tools) {
        if (tools == null) {
            return List.of();
        }
        return tools.stream()
                .map(String::toLowerCase)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted()
                .toList();
    }

    private void ensureDatabaseConfigured() {
        if (agentDataFacade == null) {
            throw new IllegalStateException("Database not configured");
        }
    }

    private AgentCreationOutcome handleExistingAgent(String allowedToolsKey) {
        if (!agentDataFacade.existsByAllowedTools(allowedToolsKey)) {
            return null;
        }

        var existing = agentDataFacade.findByAllowedTools(allowedToolsKey);
        if (existing.isEmpty()) {
            return null;
        }

        AgentCreateResponse response = buildSummaryResponse(
                existing.get(),
                "Found existing agent with same capabilities"
        );
        return new AgentCreationOutcome(response, false);
    }

    private AgentDto persistNewAgent(CreateAgentRequest request,
                                     AgentSpec agentSpec,
                                     String allowedToolsKey,
                                     String ownerId,
                                     AgentConfigDto config) {
        String agentSpecJson = serializeAgentSpec(agentSpec);
        List<String> mcpServers = extractMcpServers(agentSpec.getAllowedTools());

        return agentDataFacade.getOrCreateAgent(
                request.getName(),
                request.getDescription(),
                agentSpec.getGoal(),
                allowedToolsKey,
                agentSpecJson,
                ownerId,
                request.getTenantId(),
                mcpServers,
                config
        );
    }

    private AgentConfigDto decideConfig(AgentSpec agentSpec,
                                        CreateAgentRequest request) {
        String description = request.getDescription() != null ? request.getDescription() : "";
        var mcpData = fetchSampleMcpData(agentSpec.getAllowedTools());
        return configDeciderService.decideConfig(agentSpec, mcpData, description);
    }

    private void applyConfigToSpec(AgentSpec agentSpec, AgentConfigDto config) {
        if (agentSpec == null || config == null) {
            return;
        }
        if (config.executionMode() != null) {
            agentSpec.setExecutionMode(config.executionMode());
        }
        if (config.permissions() != null && !config.permissions().isEmpty()) {
            agentSpec.setPermissions(config.permissions());
        }
    }

    private Map<String, Object> fetchSampleMcpData(List<String> toolNames) {
        Map<String, Object> data = new java.util.HashMap<>();
        if (toolNames == null || toolNames.isEmpty()) {
            return data;
        }

        for (String toolName : toolNames) {
            MCPTool tool = toolRegistry.getTool(toolName).orElse(null);
            if (tool == null) {
                continue;
            }
            String category = tool.getCategory();
            if (category == null || category.isBlank() || data.containsKey(category)) {
                continue;
            }
            Object sample = mcpDataFetcherService.fetchSampleData(category, tool.getName());
            if (sample != null) {
                data.put(category, sample);
            }
        }

        return data;
    }

    private String serializeAgentSpec(AgentSpec agentSpec) {
        try {
            return objectMapper.writeValueAsString(agentSpec);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize agent spec", e);
        }
    }

    private List<String> extractMcpServers(List<String> tools) {
        if (tools == null) {
            return List.of();
        }
        return tools.stream()
                .map(tool -> {
                    if (tool.startsWith("mcp_")) {
                        String[] parts = tool.substring(4).split("_", 2);
                        return parts[0];
                    }
                    return tool;
                })
                .distinct()
                .toList();
    }

    private AgentCreateResponse buildSummaryResponse(AgentDto agent, String message) {
        String agentId = agent.id().toString();
        String runEndpoint = "/api/agents/" + agentId + "/run";

        return new AgentCreateResponse(
                agentId,
                agent.name(),
                agent.description() != null ? agent.description() : "",
                "existing",
                message,
                List.of(),
                agent.mcpServerNames(),
                agent.createdAt() != null ? agent.createdAt().toString() : null,
                runEndpoint,
                new AgentRunExample("POST", runEndpoint, Map.of("query", "Your query here")),
                null
        );
    }

    private AgentCreateResponse buildFullResponse(AgentDto agent,
                                                  AgentSpec agentSpec,
                                                  List<String> normalizedTools) {
        String agentId = agent.id().toString();
        String runEndpoint = "/api/agents/" + agentId + "/run";

        return new AgentCreateResponse(
                agentId,
                agent.name(),
                agent.description() != null ? agent.description() : "",
                "created",
                null,
                normalizedTools,
                agent.mcpServerNames(),
                agent.createdAt() != null ? agent.createdAt().toString() : null,
                runEndpoint,
                new AgentRunExample("POST", runEndpoint, Map.of("query", "Your query here")),
                agentSpec
        );
    }
}
