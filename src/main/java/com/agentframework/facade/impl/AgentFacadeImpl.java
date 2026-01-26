package com.agentframework.facade.impl;

import com.agentframework.common.dto.AgentDto;
import com.agentframework.data.facade.AgentDataFacade;
import com.agentframework.dto.AgentSpec;
import com.agentframework.dto.CreateAgentRequest;
import com.agentframework.facade.AgentFacade;
import com.agentframework.service.AgentCreationService;
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
    private final AgentCreationService agentCreationService;
    private final AgentDataFacade agentDataFacade;
    private final ObjectMapper objectMapper;

    @Override
    public AgentCreationResult createAgent(CreateAgentRequest request) {
        var outcome = agentCreationService.createAgent(request);
        return new AgentCreationResult(outcome.getResponse(), outcome.isCreated());
    }

    @Override
    public AgentDto refreshAgentConfig(UUID agentId) {
        log.info("Refreshing config for agent: {}", agentId);

        AgentDto agent = getAgentOrThrow(agentId);
        AgentSpec newSpec = metaAgentService.buildAgentSpec(toCreateRequest(agent));
        String agentSpecJson = serializeAgentSpec(newSpec);

        return agentDataFacade.updateAgent(
                agentId, 
                agent.description(), 
                agentSpecJson,
                newSpec.getExecutionMode(),
                joinPermissions(newSpec.getPermissions())
        );
    }

    @Override
    public AgentDto updateAgentConfig(UUID agentId, com.agentframework.common.dto.AgentConfigDto config) {
        getAgentOrThrow(agentId);

        return agentDataFacade.updateAgent(
                agentId,
                null,  // Keep description
                null,  // Keep agentSpec
                config.executionMode(),
                joinPermissions(config.permissions())
        );
    }

    @Override
    public AgentDto updateAgentMcpServers(UUID agentId, List<String> mcpServerNames) {
        // This would require adding a new method to the facade
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public Optional<AgentDto> findAgent(String userId, String name) {
        return agentDataFacade.findByCreatedBy(userId).stream()
                .filter(a -> a.name().equals(name))
                .findFirst();
    }

    @Override
    public Optional<AgentDto> findAgentById(UUID agentId) {
        return agentDataFacade.findById(agentId);
    }

    @Override
    public List<AgentDto> listAgents() {
        return agentDataFacade.findAll();
    }

    @Override
    public void deleteAgent(UUID agentId) {
        agentDataFacade.deleteAgent(agentId);
    }

    private AgentDto getAgentOrThrow(UUID agentId) {
        return agentDataFacade.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));
    }

    private CreateAgentRequest toCreateRequest(AgentDto agent) {
        CreateAgentRequest request = new CreateAgentRequest();
        request.setName(agent.name());
        request.setDescription(agent.description());
        return request;
    }

    private String serializeAgentSpec(Object spec) {
        try {
            return objectMapper.writeValueAsString(spec);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize agent spec", e);
        }
    }

    private String joinPermissions(List<String> permissions) {
        return permissions == null ? null : String.join(",", permissions);
    }

}
