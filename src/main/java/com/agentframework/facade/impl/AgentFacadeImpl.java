package com.agentframework.facade.impl;

import com.agentframework.common.dto.AgentConfigDto;
import com.agentframework.common.dto.AgentDto;
import com.agentframework.data.facade.AgentDataFacade;
import com.agentframework.dto.AgentSpec;
import com.agentframework.dto.CreateAgentRequest;
import com.agentframework.facade.AgentFacade;
import com.agentframework.service.ConfigDeciderService;
import com.agentframework.service.MCPDataFetcherService;
import com.agentframework.service.MetaAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentFacadeImpl implements AgentFacade {

    private final MetaAgentService metaAgentService;
    private final ConfigDeciderService configDeciderService;
    private final MCPDataFetcherService mcpDataFetcherService;
    private final AgentDataFacade agentDataFacade;

    @Override
    public AgentCreationResult createAgent(String userId, String name, String description) {
        log.info("Creating agent: {} for user: {}", name, userId);

        // 1. Build agent spec (selects MCP servers based on description)
        CreateAgentRequest request = new CreateAgentRequest();
        request.setUserId(userId);
        request.setName(name);
        request.setDescription(description);

        AgentSpec agentSpec = metaAgentService.buildAgentSpec(request);
        log.info("Selected MCP tools: {}", agentSpec.getAllowedTools());

        // 2. Fetch sample data from MCP servers
        Map<String, Object> mcpData = fetchMcpData(agentSpec);
        log.info("Fetched data from {} MCP sources", mcpData.size());

        // 3. Decide optimal configuration
        AgentConfigDto config = configDeciderService.decideConfig(agentSpec, mcpData, description);
        log.info("Decided config: reasoningStyle={}, temperature={}, retrieverK={}",
                config.reasoningStyle(), config.temperature(), config.retrieverK());

        // 4. Persist agent with all config
        AgentDto agent = agentDataFacade.getOrCreateAgent(
                userId,
                name,
                description,
                agentSpec.getGoal(),
                agentSpec.getAllowedTools(),
                config
        );

        log.info("Agent created: {}", agent.id());
        return new AgentCreationResult(agent, agentSpec, config);
    }

    @Override
    public AgentDto refreshAgentConfig(UUID agentId) {
        log.info("Refreshing config for agent: {}", agentId);

        AgentDto agent = agentDataFacade.findAgentById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        // Build spec from existing agent
        AgentSpec spec = AgentSpec.builder()
                .agentName(agent.botId())
                .goal(agent.goal())
                .allowedTools(agent.mcpServerNames())
                .executionMode(agent.executionMode())
                .permissions(agent.permissions())
                .build();

        // Fetch fresh data and re-decide config
        Map<String, Object> mcpData = fetchMcpData(spec);
        AgentConfigDto newConfig = configDeciderService.decideConfig(spec, mcpData, agent.description());

        return agentDataFacade.updateAgentConfig(agentId, newConfig);
    }

    @Override
    public AgentDto updateAgentConfig(UUID agentId, AgentConfigDto config) {
        return agentDataFacade.updateAgentConfig(agentId, config);
    }

    @Override
    public AgentDto updateAgentMcpServers(UUID agentId, List<String> mcpServerNames) {
        return agentDataFacade.updateAgentMcpServers(agentId, mcpServerNames);
    }

    @Override
    public Optional<AgentDto> findAgent(String userId, String botId) {
        return agentDataFacade.findAgent(userId, botId);
    }

    @Override
    public Optional<AgentDto> findAgentById(UUID agentId) {
        return agentDataFacade.findAgentById(agentId);
    }

    @Override
    public void deleteAgent(UUID agentId) {
        agentDataFacade.deleteAgent(agentId);
    }

    private Map<String, Object> fetchMcpData(AgentSpec agentSpec) {
        Map<String, Object> mcpData = new HashMap<>();

        for (String toolName : agentSpec.getAllowedTools()) {
            try {
                String category = extractCategory(toolName);
                if (category != null && !mcpData.containsKey(category)) {
                    Object data = mcpDataFetcherService.fetchSampleData(category, toolName);
                    if (data != null) {
                        mcpData.put(category, data);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to fetch data for tool {}: {}", toolName, e.getMessage());
            }
        }

        return mcpData;
    }

    private String extractCategory(String toolName) {
        String lower = toolName.toLowerCase();
        if (lower.contains("jira")) return "jira";
        if (lower.contains("confluence")) return "confluence";
        if (lower.contains("github")) return "github";
        return null;
    }
}
