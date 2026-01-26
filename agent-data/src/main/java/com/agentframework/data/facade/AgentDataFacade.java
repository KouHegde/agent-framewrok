package com.agentframework.data.facade;

import com.agentframework.common.dto.AgentDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Facade interface for agent data operations.
 */
public interface AgentDataFacade {

    /**
     * Find agent by allowed tools (for deduplication).
     * This is the primary lookup method.
     *
     * @param allowedTools sorted comma-separated tool names
     * @return optional containing the agent if found
     */
    Optional<AgentDto> findByAllowedTools(String allowedTools);

    /**
     * Check if agent exists with given tools.
     */
    boolean existsByAllowedTools(String allowedTools);

    /**
     * Create a new agent.
     *
     * @param name         display name
     * @param description  what the agent does
     * @param goal         agent's objective
     * @param allowedTools sorted comma-separated tool names
     * @param agentSpec    full AgentSpec as JSON
     * @param createdBy    who created it
     * @param tenantId     organization/workspace
     * @param mcpServers   list of MCP server names
     * @return the created agent DTO
     */
    AgentDto createAgent(String name, String description, String goal,
                         String allowedTools, String agentSpec,
                         String createdBy, String tenantId,
                         List<String> mcpServers,
                         com.agentframework.common.dto.AgentConfigDto config,
                         String downstreamStatus,
                         String downstreamAgentId);

    /**
     * Get or create agent based on allowed tools.
     * If agent with same tools exists, returns existing.
     * Otherwise, creates new agent.
     */
    AgentDto getOrCreateAgent(String name, String description, String goal,
                              String allowedTools, String agentSpec,
                              String createdBy, String tenantId,
                              List<String> mcpServers,
                              com.agentframework.common.dto.AgentConfigDto config,
                              String downstreamStatus,
                              String downstreamAgentId);

    /**
     * Update agent configuration.
     */
    AgentDto updateAgent(UUID agentId, String description, String agentSpec,
                         String executionMode, String permissions);

    /**
     * Update agent's Python AgentBrain response.
     */
    AgentDto updateBrainResponse(UUID agentId, String brainAgentId,
                                  String systemPrompt, Integer maxSteps);

    /**
     * Find agent by ID.
     */
    Optional<AgentDto> findById(UUID agentId);

    /**
     * List all agents.
     */
    List<AgentDto> findAll();

    /**
     * List agents by creator.
     */
    List<AgentDto> findByCreatedBy(String createdBy);

    /**
     * List agents by tenant.
     */
    List<AgentDto> findByTenantId(String tenantId);

    /**
     * Delete agent by ID.
     */
    void deleteAgent(UUID agentId);
}
