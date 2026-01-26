package com.agentframework.data.facade.impl;

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
    @Transactional(readOnly = true)
    public Optional<AgentDto> findByAllowedTools(String allowedTools) {
        return agentRepository.findByAllowedTools(allowedTools).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByAllowedTools(String allowedTools) {
        return agentRepository.existsByAllowedTools(allowedTools);
    }

    @Override
    @Transactional
    public AgentDto createAgent(String name, String description, String goal,
                                 String allowedTools, String agentSpec,
                                 String createdBy, String tenantId,
                                 List<String> mcpServers) {
        
        Agent agent = new Agent(name, description, allowedTools);
        agent.setGoal(goal);
        agent.setAgentSpec(agentSpec);
        agent.setCreatedBy(createdBy);
        agent.setTenantId(tenantId);
        
        // Add MCP servers
        if (mcpServers != null) {
            mcpServers.forEach(serverName -> 
                agent.addMcpServer(new AgentMcpServer(serverName)));
        }

        Agent saved = agentRepository.save(agent);
        log.info("Created agent {} with ID: {}", name, saved.getId());
        return toDto(saved);
    }

    @Override
    @Transactional
    public AgentDto getOrCreateAgent(String name, String description, String goal,
                                      String allowedTools, String agentSpec,
                                      String createdBy, String tenantId,
                                      List<String> mcpServers) {
        
        // Check if agent with same tools already exists
        Optional<Agent> existing = agentRepository.findByAllowedTools(allowedTools);
        if (existing.isPresent()) {
            log.info("Found existing agent with same tools: {}", existing.get().getId());
            return toDto(existing.get());
        }

        // Create new agent
        return createAgent(name, description, goal, allowedTools, agentSpec, 
                          createdBy, tenantId, mcpServers);
    }

    @Override
    @Transactional
    public AgentDto updateAgent(UUID agentId, String description, String agentSpec,
                                 String executionMode, String permissions) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        if (description != null) {
            agent.setDescription(description);
        }
        if (agentSpec != null) {
            agent.setAgentSpec(agentSpec);
        }
        if (executionMode != null) {
            agent.setExecutionMode(executionMode);
        }
        if (permissions != null) {
            agent.setPermissions(permissions);
        }

        Agent updated = agentRepository.save(agent);
        log.info("Updated agent: {}", agentId);
        return toDto(updated);
    }

    @Override
    @Transactional
    public AgentDto updateBrainResponse(UUID agentId, String brainAgentId,
                                         String systemPrompt, Integer maxSteps) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        if (brainAgentId != null) {
            agent.setBrainAgentId(brainAgentId);
        }
        if (systemPrompt != null) {
            agent.setSystemPrompt(systemPrompt);
        }
        if (maxSteps != null) {
            agent.setMaxSteps(maxSteps);
        }

        Agent updated = agentRepository.save(agent);
        log.info("Updated brain response for agent: {}", agentId);
        return toDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentDto> findById(UUID agentId) {
        return agentRepository.findById(agentId).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentDto> findAll() {
        return agentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentDto> findByCreatedBy(String createdBy) {
        return agentRepository.findByCreatedBy(createdBy).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentDto> findByTenantId(String tenantId) {
        return agentRepository.findByTenantId(tenantId).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public void deleteAgent(UUID agentId) {
        agentRepository.deleteById(agentId);
        log.info("Deleted agent: {}", agentId);
    }

    private AgentDto toDto(Agent agent) {
        List<String> mcpServerNames = agent.getMcpServers().stream()
                .map(AgentMcpServer::getServerName)
                .toList();

        return new AgentDto(
                agent.getId(),
                agent.getName(),
                agent.getDescription(),
                agent.getGoal(),
                agent.getAllowedTools(),
                agent.getAgentSpec(),
                agent.getCreatedBy(),
                agent.getTenantId(),
                mcpServerNames,
                agent.getRagScope(),
                agent.getReasoningStyle(),
                agent.getTemperature(),
                agent.getRetrieverType(),
                agent.getRetrieverK(),
                agent.getExecutionMode(),
                agent.getPermissions(),
                agent.getSystemPrompt(),
                agent.getMaxSteps(),
                agent.getBrainAgentId(),
                agent.getStatus(),
                agent.getCreatedAt(),
                agent.getUpdatedAt()
        );
    }
}
