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
    public AgentDto getOrCreateAgent(String userId, String botId, String description, String goal,
                                     List<String> mcpServerNames, AgentConfigDto config) {
        validate(userId, botId);

        Optional<Agent> existing = agentRepository.findByUserIdAndBotId(userId, botId);
        if (existing.isPresent()) {
            log.info("Found existing agent for user: {}, bot: {}", userId, botId);
            return toDto(existing.get());
        }

        Agent agent = new Agent(botId, userId);
        agent.setDescription(description);
        agent.setGoal(goal);
        addMcpServers(agent, mcpServerNames);

        // Apply config if provided
        if (config != null) {
            applyConfig(agent, config);
        }

        Agent saved = agentRepository.save(agent);
        log.info("Created agent {} for user: {}", saved.getId(), userId);
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
    public AgentDto updateAgentMcpServers(UUID agentId, List<String> mcpServerNames) {
        Agent agent = findAgentOrThrow(agentId);

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
        Agent agent = findAgentOrThrow(agentId);
        applyConfig(agent, config);
        Agent updated = agentRepository.save(agent);
        log.info("Updated config for agent: {}", agentId);
        return toDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentDto> findAgent(String userId, String botId) {
        return agentRepository.findByUserIdAndBotId(userId, botId).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentDto> findAgentById(UUID agentId) {
        return agentRepository.findById(agentId).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean agentExists(String userId, String botId) {
        return agentRepository.existsByUserIdAndBotId(userId, botId);
    }

    @Override
    @Transactional
    public void deleteAgent(UUID agentId) {
        agentRepository.deleteById(agentId);
        log.info("Deleted agent: {}", agentId);
    }

    private Agent findAgentOrThrow(UUID agentId) {
        return agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));
    }

    private void validate(String userId, String botId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID cannot be null or blank");
        }
        if (botId == null || botId.isBlank()) {
            throw new IllegalArgumentException("Bot ID cannot be null or blank");
        }
    }

    private void addMcpServers(Agent agent, List<String> serverNames) {
        if (serverNames != null) {
            serverNames.forEach(name -> agent.addMcpServer(new AgentMcpServer(name)));
        }
    }

    private AgentDto toDto(Agent agent) {
        List<String> mcpServerNames = agent.getMcpServers().stream()
                .map(AgentMcpServer::getServerName)
                .toList();

        return new AgentDto(
                agent.getId(),
                agent.getBotId(),
                agent.getUserId(),
                agent.getDescription(),
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
