package com.agentframework.data.facade;

import com.agentframework.common.dto.AgentDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Facade interface for agent data operations.
 * This is the main entry point for other modules to interact with agent data.
 */
public interface AgentDataFacade {

    /**
     * Creates a new agent or returns existing one if already present.
     *
     * @param userId         the user identifier
     * @param botId          the bot identifier
     * @param userConfig     user-specific configuration (optional)
     * @param mcpServerNames list of MCP server names required for this agent
     * @return the created or existing agent DTO
     */
    AgentDto getOrCreateAgent(String userId, String botId, String userConfig, List<String> mcpServerNames);

    /**
     * Updates an existing agent's configuration and MCP servers.
     *
     * @param agentId        the agent ID
     * @param userConfig     new user configuration (null to keep existing)
     * @param mcpServerNames new list of MCP servers (null to keep existing)
     * @return the updated agent DTO
     */
    AgentDto updateAgent(UUID agentId, String userConfig, List<String> mcpServerNames);

    /**
     * Finds an agent by user and bot ID.
     *
     * @param userId the user identifier
     * @param botId  the bot identifier
     * @return optional containing the agent if found
     */
    Optional<AgentDto> findAgent(String userId, String botId);

    /**
     * Finds an agent by its ID.
     *
     * @param agentId the agent ID
     * @return optional containing the agent if found
     */
    Optional<AgentDto> findAgentById(UUID agentId);

    /**
     * Checks if an agent already exists for the given user and bot.
     *
     * @param userId the user identifier
     * @param botId  the bot identifier
     * @return true if an agent exists
     */
    boolean agentExists(String userId, String botId);

    /**
     * Deletes an agent by its ID.
     *
     * @param agentId the agent ID
     */
    void deleteAgent(UUID agentId);
}
