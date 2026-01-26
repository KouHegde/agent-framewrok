package com.agentframework.facade;

import com.agentframework.common.dto.AgentConfigDto;
import com.agentframework.common.dto.AgentDto;
import com.agentframework.dto.AgentCreateResponse;
import com.agentframework.dto.CreateAgentRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Facade for agent operations.
 * Orchestrates all business logic for agent CRUD.
 */
public interface AgentFacade {

    /**
     * Creates a new agent with dynamic configuration.
     * Orchestrates: MCP server selection → config decision → persistence
     */
    AgentCreationResult createAgent(CreateAgentRequest request);

    /**
     * Refreshes an agent's configuration based on fresh MCP data.
     */
    AgentDto refreshAgentConfig(UUID agentId);

    /**
     * Updates agent configuration manually.
     */
    AgentDto updateAgentConfig(UUID agentId, AgentConfigDto config);

    /**
     * Updates agent's MCP servers.
     */
    AgentDto updateAgentMcpServers(UUID agentId, List<String> mcpServerNames);

    /**
     * Finds an agent by user and bot ID.
     */
    Optional<AgentDto> findAgent(String userId, String botId);

    /**
     * Finds an agent by ID.
     */
    Optional<AgentDto> findAgentById(UUID agentId);

    /**
     * Lists all agents.
     */
    List<AgentDto> listAgents();

    /**
     * Deletes an agent.
     */
    void deleteAgent(UUID agentId);

    /**
     * Result of agent creation containing all relevant data.
     */
    record AgentCreationResult(
            AgentCreateResponse response,
            boolean created
    ) {}
}
