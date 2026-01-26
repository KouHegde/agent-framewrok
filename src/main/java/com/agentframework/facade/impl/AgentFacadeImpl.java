package com.agentframework.facade.impl;

import com.agentframework.common.dto.AgentDto;
import com.agentframework.data.facade.AgentDataFacade;
import com.agentframework.dto.AgentSpec;
import com.agentframework.dto.CreateAgentRequest;
import com.agentframework.facade.AgentFacade;
import com.agentframework.service.MetaAgentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentFacadeImpl implements AgentFacade {

    private final MetaAgentService metaAgentService;
    private final AgentDataFacade agentDataFacade;
    private final ObjectMapper objectMapper;

    @Override
    public AgentCreationResult createAgent(String userId, String name, String description) {
        log.info("Creating agent: {} for user: {}", name, userId);

        // Build agent spec
        CreateAgentRequest request = new CreateAgentRequest();
        request.setName(name);
        request.setDescription(description);

        AgentSpec agentSpec = metaAgentService.buildAgentSpec(request);
        log.info("Selected MCP tools: {}", agentSpec.getAllowedTools());

        // Create sorted allowed_tools key
        List<String> sortedTools = agentSpec.getAllowedTools().stream().sorted().toList();
        String allowedToolsKey = String.join(",", sortedTools);

        // Serialize agent spec
        String agentSpecJson;
        try {
            agentSpecJson = objectMapper.writeValueAsString(agentSpec);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize agent spec", e);
        }

        // Extract MCP server names
        List<String> mcpServers = agentSpec.getAllowedTools().stream()
                .map(tool -> {
                    if (tool.startsWith("mcp_")) {
                        String[] parts = tool.substring(4).split("_", 2);
                        return parts[0];
                    }
                    return tool;
                })
                .distinct()
                .toList();

        // Get or create agent
        AgentDto agent = agentDataFacade.getOrCreateAgent(
                name,
                description,
                agentSpec.getGoal(),
                allowedToolsKey,
                agentSpecJson,
                userId,
                null,  // tenantId
                mcpServers
        );

        log.info("Agent created: {}", agent.id());
        return new AgentCreationResult(agent, agentSpec, null);
    }

    @Override
    public AgentDto refreshAgentConfig(UUID agentId) {
        log.info("Refreshing config for agent: {}", agentId);

        AgentDto agent = agentDataFacade.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        // Re-build spec from description
        CreateAgentRequest request = new CreateAgentRequest();
        request.setName(agent.name());
        request.setDescription(agent.description());

        AgentSpec newSpec = metaAgentService.buildAgentSpec(request);

        String agentSpecJson;
        try {
            agentSpecJson = objectMapper.writeValueAsString(newSpec);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize agent spec", e);
        }

        return agentDataFacade.updateAgent(
                agentId, 
                agent.description(), 
                agentSpecJson,
                newSpec.getExecutionMode(),
                String.join(",", newSpec.getPermissions())
        );
    }

    @Override
    public AgentDto updateAgentConfig(UUID agentId, com.agentframework.common.dto.AgentConfigDto config) {
        // Update individual config fields
        AgentDto agent = agentDataFacade.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));
        
        return agentDataFacade.updateAgent(
                agentId,
                null,  // Keep description
                null,  // Keep agentSpec
                config.executionMode(),
                config.permissions() != null ? String.join(",", config.permissions()) : null
        );
    }

    @Override
    public AgentDto updateAgentMcpServers(UUID agentId, List<String> mcpServerNames) {
        // This would require adding a new method to the facade
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public Optional<AgentDto> findAgent(String userId, String name) {
        // Find by allowed tools or other criteria
        return agentDataFacade.findByCreatedBy(userId).stream()
                .filter(a -> a.name().equals(name))
                .findFirst();
    }

    @Override
    public Optional<AgentDto> findAgentById(UUID agentId) {
        return agentDataFacade.findById(agentId);
    }

    @Override
    public void deleteAgent(UUID agentId) {
        agentDataFacade.deleteAgent(agentId);
    }
}
