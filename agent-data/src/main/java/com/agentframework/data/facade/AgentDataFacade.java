package com.agentframework.data.facade;

import com.agentframework.common.dto.AgentConfigDto;
import com.agentframework.common.dto.AgentDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Facade interface for agent data operations.
 */
public interface AgentDataFacade {

    /**
     * Creates a new agent or returns existing one if already present (by name).
     *
     * @param name           the agent name (unique identifier)
     * @param description    the agent description
     * @param agentSpec      the agent specification (JSON string)
     * @param mcpServerNames list of MCP server names required for this agent
     * @return the created or existing agent DTO
     */
    AgentDto getOrCreateAgent(String name, String description, String agentSpec, List<String> mcpServerNames);

    /**
     * Creates a new agent or returns existing one (with user context).
     */
    AgentDto getOrCreateAgent(String name, String description, String agentSpec, String userId, List<String> mcpServerNames);

    /**
     * Updates an existing agent's configuration.
     *
     * @param agentId        the agent ID
     * @param description    new description (null to keep existing)
     * @param agentSpec      new agent spec (null to keep existing)
     * @param mcpServerNames new list of MCP servers (null to keep existing)
     * @return the updated agent DTO
     */
    AgentDto updateAgent(UUID agentId, String description, String agentSpec, List<String> mcpServerNames);

    /**
     * Finds an agent by name.
     *
     * @param name the agent name
     * @return optional containing the agent if found
     */
    Optional<AgentDto> findAgentByName(String name);

    /**
     * Finds an agent by its ID.
     */
    Optional<AgentDto> findAgentById(UUID agentId);

    /**
     * Lists all agents.
     *
     * @return list of all agents
     */
    List<AgentDto> listAllAgents();

    /**
     * Lists agents for a specific user.
     *
     * @param userId the user identifier
     * @return list of agents for the user
     */
    List<AgentDto> listAgentsByUser(String userId);

    /**
     * Checks if an agent already exists with the given name.
     *
     * @param name the agent name
     * @return true if an agent exists
     */
    boolean agentExists(String name);

    /**
     * Deletes an agent by its ID.
     */
    void deleteAgent(UUID agentId);

    /**
     * Updates agent configuration (RAG, reasoning, etc.)
     */
    AgentDto updateAgentConfig(UUID agentId, AgentConfigDto config);
}
