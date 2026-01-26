package com.agentframework.data.facade.impl;

import com.agentframework.common.dto.AgentConfigDto;
import com.agentframework.common.dto.AgentDto;
import com.agentframework.data.entity.Agent;
import com.agentframework.data.entity.AgentMcpServer;
import com.agentframework.data.facade.AgentDataFacade;
import com.agentframework.data.repository.AgentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class AgentDataFacadeImpl implements AgentDataFacade {

    private static final Logger log = LoggerFactory.getLogger(AgentDataFacadeImpl.class);

    private final AgentRepository agentRepository;

    public AgentDataFacadeImpl(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    @Override
    @Transactional
    public AgentDto getOrCreateAgent(String name, String description, String agentSpec, List<String> mcpServerNames) {
        return getOrCreateAgent(name, description, agentSpec, null, mcpServerNames);
    }

    @Override
    @Transactional
    public AgentDto getOrCreateAgent(String name, String description, String agentSpec, String userId, List<String> mcpServerNames) {
        validateName(name);

        Optional<Agent> existing = agentRepository.findByName(name);
        if (existing.isPresent()) {
            log.info("Found existing agent with name: {}", name);
            return toDto(existing.get());
        }

        Agent agent = new Agent(name, description, userId);
        agent.setAgentSpec(agentSpec);
        addMcpServers(agent, mcpServerNames);

        Agent saved = agentRepository.save(agent);
        log.info("Created agent {} with name: {}", saved.getId(), name);
        return toDto(saved);
    }

    private void applyConfig(Agent agent, AgentConfigDto config) {
        if (config.ragScope() != null) {
            agent.setRagScope(config.ragScope());
        }
        if (config.reasoningStyle() != null) {
            agent.setReasoningStyle(config.reasoningStyle());
        }
        if (config.temperature() != null) {
            agent.setTemperature(config.temperature());
        }
        if (config.retrieverType() != null) {
            agent.setRetrieverType(config.retrieverType());
        }
        if (config.retrieverK() != null) {
            agent.setRetrieverK(config.retrieverK());
        }
        if (config.executionMode() != null) {
            agent.setExecutionMode(config.executionMode());
        }
        if (config.permissions() != null) {
            agent.setPermissions(config.permissions());
        }
    }

    @Override
    @Transactional
    public AgentDto updateAgent(UUID agentId, String description, String agentSpec, List<String> mcpServerNames) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        if (description != null) {
            agent.setDescription(description);
        }
        if (agentSpec != null) {
            agent.setAgentSpec(agentSpec);
        }
        if (mcpServerNames != null) {
            agent.getMcpServers().clear();
            addMcpServers(agent, mcpServerNames);
        }

        Agent updated = agentRepository.save(agent);
        log.info("Updated MCP servers for agent: {}", agentId);
        return toDto(updated);
    }

    @Override
    @Transactional
    public AgentDto updateAgentConfig(UUID agentId, AgentConfigDto config) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));
        applyConfig(agent, config);
        Agent updated = agentRepository.save(agent);
        log.info("Updated config for agent: {}", agentId);
        return toDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentDto> findAgentByName(String name) {
        return agentRepository.findByName(name).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentDto> findAgentById(UUID agentId) {
        return agentRepository.findById(agentId).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentDto> listAllAgents() {
        return agentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentDto> listAgentsByUser(String userId) {
        return agentRepository.findByUserId(userId).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean agentExists(String name) {
        return agentRepository.existsByName(name);
    }

    @Override
    @Transactional
    public void deleteAgent(UUID agentId) {
        agentRepository.deleteById(agentId);
        log.info("Deleted agent: {}", agentId);
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Agent name cannot be null or blank");
        }
    }

    private void addMcpServers(Agent agent, List<String> serverNames) {
        if (serverNames != null) {
            serverNames.forEach(serverName -> agent.addMcpServer(new AgentMcpServer(serverName)));
        }
    }

    private AgentDto toDto(Agent agent) {
        List<String> mcpServerNames = agent.getMcpServers().stream()
                .map(AgentMcpServer::getServerName)
                .toList();

        return new AgentDto(
                agent.getId(),
                agent.getName(),
                agent.getDescription(),
                agent.getAgentSpec(),
                agent.getUserId(),
                agent.getGoal(),
                mcpServerNames,
                agent.getRagScope(),
                agent.getReasoningStyle(),
                agent.getTemperature(),
                agent.getRetrieverType(),
                agent.getRetrieverK(),
                agent.getExecutionMode(),
                agent.getPermissions(),
                agent.getCreatedAt(),
                agent.getUpdatedAt()
        );
    }
}
