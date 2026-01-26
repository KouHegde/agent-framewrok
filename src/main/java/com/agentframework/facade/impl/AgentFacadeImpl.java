package com.agentframework.facade.impl;

import com.agentframework.common.dto.AgentConfigDto;
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

        // 1. Build agent spec (selects MCP tools based on description)
        CreateAgentRequest request = new CreateAgentRequest();
        request.setName(name);
        request.setDescription(description);

        AgentSpec agentSpec = metaAgentService.buildAgentSpec(request);
        log.info("Selected MCP tools: {}", agentSpec.getAllowedTools());

        // 2. Serialize agent spec to JSON
        String agentSpecJson;
        try {
            agentSpecJson = objectMapper.writeValueAsString(agentSpec);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize agent spec", e);
        }

        // 3. Persist agent
        AgentDto agent = agentDataFacade.getOrCreateAgent(
                name,
                description,
                agentSpecJson,
                userId,
                agentSpec.getAllowedTools()
        );

        log.info("Agent created: {}", agent.id());
        return new AgentCreationResult(agent, agentSpec, null);
    }

    @Override
    public AgentDto refreshAgentConfig(UUID agentId) {
        log.info("Refreshing config for agent: {}", agentId);

        AgentDto agent = agentDataFacade.findAgentById(agentId)
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

        return agentDataFacade.updateAgent(agentId, agent.description(), agentSpecJson, newSpec.getAllowedTools());
    }

    @Override
    public AgentDto updateAgentConfig(UUID agentId, AgentConfigDto config) {
        return agentDataFacade.updateAgentConfig(agentId, config);
    }

    @Override
    public AgentDto updateAgentMcpServers(UUID agentId, List<String> mcpServerNames) {
        return agentDataFacade.updateAgent(agentId, null, null, mcpServerNames);
    }

    @Override
    public Optional<AgentDto> findAgent(String userId, String name) {
        return agentDataFacade.findAgentByName(name);
    }

    @Override
    public Optional<AgentDto> findAgentById(UUID agentId) {
        return agentDataFacade.findAgentById(agentId);
    }

    @Override
    public void deleteAgent(UUID agentId) {
        agentDataFacade.deleteAgent(agentId);
    }
}
