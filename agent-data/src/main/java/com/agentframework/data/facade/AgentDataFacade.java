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
     * Creates a new agent with full configuration, or returns existing one.
     *
     * @param userId         the user identifier
     * @param botId          the bot identifier
     * @param description    agent description
     * @param goal           agent goal
     * @param mcpServerNames list of MCP server names
     * @param config         agent configuration (RAG, reasoning, retriever settings)
     * @return the created or existing agent DTO
     */
    AgentDto getOrCreateAgent(String userId, String botId, String description, String goal,
                              List<String> mcpServerNames, AgentConfigDto config);

    /**
     * Updates an existing agent's MCP servers.
     */
    AgentDto updateAgentMcpServers(UUID agentId, List<String> mcpServerNames);

    /**
     * Updates an existing agent's configuration.
     */
    AgentDto updateAgentConfig(UUID agentId, AgentConfigDto config);

    /**
     * Finds an agent by user and bot ID.
     */
    Optional<AgentDto> findAgent(String userId, String botId);

    /**
     * Finds an agent by its ID.
     */
    Optional<AgentDto> findAgentById(UUID agentId);

    /**
     * Checks if an agent already exists for the given user and bot.
     */
    boolean agentExists(String userId, String botId);

    /**
     * Deletes an agent by its ID.
     */
    void deleteAgent(UUID agentId);
}
