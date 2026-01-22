package com.agentframework.data.facade.impl;

import com.agentframework.data.dto.AgentDto;
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
    public AgentDto getOrCreateAgent(String userId, String botId, String userConfig, List<String> mcpServerNames) {
        validate(userId, botId);

        Optional<Agent> existing = agentRepository.findByUserIdAndBotId(userId, botId);
        if (existing.isPresent()) {
            log.info("Found existing agent for user: {}, bot: {}", userId, botId);
            return toDto(existing.get());
        }

        Agent agent = new Agent(botId, userId);
        agent.setUserConfig(userConfig);
        addMcpServers(agent, mcpServerNames);

        Agent saved = agentRepository.save(agent);
        log.info("Created agent {} for user: {}", saved.getId(), userId);
        return toDto(saved);
    }

    @Override
    @Transactional
    public AgentDto updateAgent(UUID agentId, String userConfig, List<String> mcpServerNames) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        if (userConfig != null) {
            agent.setUserConfig(userConfig);
        }
        if (mcpServerNames != null) {
            agent.getMcpServers().clear();
            addMcpServers(agent, mcpServerNames);
        }

        Agent updated = agentRepository.save(agent);
        log.info("Updated agent: {}", agentId);
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
                agent.getUserConfig(),
                mcpServerNames,
                agent.getCreatedAt(),
                agent.getUpdatedAt()
        );
    }
}
