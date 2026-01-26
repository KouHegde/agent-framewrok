package com.agentframework.service;

import com.agentframework.common.dto.AgentDto;
import com.agentframework.common.dto.AgentConfigDto;
import com.agentframework.data.facade.AgentDataFacade;
import com.agentframework.dto.AgentCreateResponse;
import com.agentframework.dto.AgentCreationOutcome;
import com.agentframework.dto.AgentRunExample;
import com.agentframework.dto.AgentSpec;
import com.agentframework.dto.CreateAgentRequest;
import com.agentframework.dto.DownstreamAgentCreateRequest;
import com.agentframework.dto.DownstreamAgentCreateResponse;
import com.agentframework.dto.DownstreamPolicy;
import com.agentframework.dto.DownstreamTool;
import com.agentframework.registry.MCPTool;
import com.agentframework.registry.MCPToolRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentCreationService {

    private final MetaAgentService metaAgentService;
    private final MCPToolRegistry toolRegistry;
    private final MCPDataFetcherService mcpDataFetcherService;
    private final ConfigDeciderService configDeciderService;
    private final DownstreamAgentService downstreamAgentService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private AgentDataFacade agentDataFacade;

    public AgentCreationOutcome createAgent(CreateAgentRequest request) {
        String ownerId = resolveOwnerId(request);
        String tenantId = resolveTenantId(request);
        log.info("Creating agent: {} for user: {}", request.getName(), ownerId);

        AgentSpec agentSpec = buildAgentSpec(request);
        List<String> normalizedTools = normalizeTools(agentSpec.getAllowedTools());
        String allowedToolsKey = String.join(",", normalizedTools);

        ensureDatabaseConfigured();

        AgentCreationOutcome existing = handleExistingAgent(allowedToolsKey);
        if (existing != null) {
            return existing;
        }

        DecisionFetchResult fetchResult = fetchDecisionData(agentSpec.getAllowedTools());
        AgentConfigDto config = decideConfig(agentSpec, request, fetchResult.data());
        applyConfigToSpec(agentSpec, config);
        DownstreamAgentCreateResponse downstream = createDownstreamAgent(request, agentSpec, config, ownerId, tenantId);
        String downstreamStatus = downstream.getStatus() != null ? downstream.getStatus() : "unknown";
        String downstreamAgentId = downstream.getAgentId();

        AgentDto agent = persistNewAgent(request, agentSpec, allowedToolsKey, ownerId, config,
                downstreamStatus, downstreamAgentId);
        log.info("Agent created: {}", agent.id());
        AgentCreateResponse response = buildFullResponse(agent, agentSpec, normalizedTools,
                fetchResult.status(), fetchResult.message(), downstreamStatus, downstreamAgentId);
        return new AgentCreationOutcome(response, true);
    }

    private AgentSpec buildAgentSpec(CreateAgentRequest request) {
        AgentSpec agentSpec = metaAgentService.buildAgentSpec(request);
        log.info("Selected MCP tools: {}", agentSpec.getAllowedTools());
        toolRegistry.ensureToolsPersisted(agentSpec.getAllowedTools());
        return agentSpec;
    }

    private List<String> normalizeTools(List<String> tools) {
        if (tools == null) {
            return List.of();
        }
        return tools.stream()
                .map(String::toLowerCase)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted()
                .toList();
    }

    private void ensureDatabaseConfigured() {
        if (agentDataFacade == null) {
            throw new IllegalStateException("Database not configured");
        }
    }

    private AgentCreationOutcome handleExistingAgent(String allowedToolsKey) {
        if (!agentDataFacade.existsByAllowedTools(allowedToolsKey)) {
            return null;
        }

        var existing = agentDataFacade.findByAllowedTools(allowedToolsKey);
        if (existing.isEmpty()) {
            return null;
        }

        AgentCreateResponse response = buildSummaryResponse(
                existing.get(),
                "Found existing agent with same capabilities",
                Map.of(),
                "Fetch skipped: existing agent"
        );
        return new AgentCreationOutcome(response, false);
    }

    private AgentDto persistNewAgent(CreateAgentRequest request,
                                     AgentSpec agentSpec,
                                     String allowedToolsKey,
                                     String ownerId,
                                     AgentConfigDto config,
                                     String downstreamStatus,
                                     String downstreamAgentId) {
        String agentSpecJson = serializeAgentSpec(agentSpec);
        List<String> mcpServers = extractMcpServers(agentSpec.getAllowedTools());

        return agentDataFacade.getOrCreateAgent(
                request.getName(),
                request.getDescription(),
                agentSpec.getGoal(),
                allowedToolsKey,
                agentSpecJson,
                ownerId,
                request.getTenantId(),
                mcpServers,
                config,
                downstreamStatus,
                downstreamAgentId
        );
    }

    private AgentConfigDto decideConfig(AgentSpec agentSpec,
                                        CreateAgentRequest request,
                                        Map<String, Object> mcpData) {
        String description = request.getDescription() != null ? request.getDescription() : "";
        return configDeciderService.decideConfig(agentSpec, mcpData, description);
    }

    private void applyConfigToSpec(AgentSpec agentSpec, AgentConfigDto config) {
        if (agentSpec == null || config == null) {
            return;
        }
        if (config.executionMode() != null) {
            agentSpec.setExecutionMode(config.executionMode());
        }
        if (config.permissions() != null && !config.permissions().isEmpty()) {
            agentSpec.setPermissions(config.permissions());
        }
    }

    private DecisionFetchResult fetchDecisionData(List<String> toolNames) {
        Map<String, Object> data = new java.util.HashMap<>();
        Map<String, String> status = new java.util.LinkedHashMap<>();
        if (toolNames == null || toolNames.isEmpty()) {
            return new DecisionFetchResult(data, status, "No MCP tools selected");
        }

        for (String toolName : toolNames) {
            MCPTool tool = toolRegistry.getTool(toolName).orElse(null);
            if (tool == null) {
                status.put(toolName, "missing_in_registry");
                continue;
            }
            String category = tool.getCategory();
            if (category == null || category.isBlank() || data.containsKey(category)) {
                if (category == null || category.isBlank()) {
                    status.put(toolName, "missing_category");
                }
                continue;
            }
            Map<String, Object> sample = mcpDataFetcherService.fetchDecisionData(category, tool.getName());
            data.put(category, sample);
            status.put(category, deriveStatus(sample));
            log.debug("Decision fetch for {} via {}: {}", category, tool.getName(), status.get(category));
        }

        String message = buildFetchMessage(status);
        log.info("MCP decision fetch status: {}", message);
        return new DecisionFetchResult(data, status, message);
    }

    private String serializeAgentSpec(AgentSpec agentSpec) {
        try {
            return objectMapper.writeValueAsString(agentSpec);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize agent spec", e);
        }
    }

    private List<String> extractMcpServers(List<String> tools) {
        if (tools == null) {
            return List.of();
        }
        return tools.stream()
                .map(tool -> {
                    if (tool.startsWith("mcp_")) {
                        String[] parts = tool.substring(4).split("_", 2);
                        return parts[0];
                    }
                    return tool;
                })
                .distinct()
                .toList();
    }

    private AgentCreateResponse buildSummaryResponse(AgentDto agent,
                                                     String message,
                                                     Map<String, String> status,
                                                     String fetchMessage) {
        String agentId = agent.id().toString();
        String runEndpoint = "/api/agents/" + agentId + "/run";

        return new AgentCreateResponse(
                agentId,
                agent.name(),
                agent.description() != null ? agent.description() : "",
                "existing",
                message,
                List.of(),
                agent.mcpServerNames(),
                agent.createdAt() != null ? agent.createdAt().toString() : null,
                runEndpoint,
                new AgentRunExample("POST", runEndpoint, Map.of("query", "Your query here")),
                null,
                status,
                fetchMessage,
                agent.downstreamStatus(),
                agent.downstreamAgentId()
        );
    }

    private AgentCreateResponse buildFullResponse(AgentDto agent,
                                                  AgentSpec agentSpec,
                                                  List<String> normalizedTools,
                                                  Map<String, String> status,
                                                  String fetchMessage,
                                                  String downstreamStatus,
                                                  String downstreamAgentId) {
        String agentId = agent.id().toString();
        String runEndpoint = "/api/agents/" + agentId + "/run";

        return new AgentCreateResponse(
                agentId,
                agent.name(),
                agent.description() != null ? agent.description() : "",
                "created",
                null,
                normalizedTools,
                agent.mcpServerNames(),
                agent.createdAt() != null ? agent.createdAt().toString() : null,
                runEndpoint,
                new AgentRunExample("POST", runEndpoint, Map.of("query", "Your query here")),
                agentSpec,
                status,
                fetchMessage,
                downstreamStatus,
                downstreamAgentId
        );
    }

    private DownstreamAgentCreateResponse createDownstreamAgent(CreateAgentRequest request,
                                                                AgentSpec agentSpec,
                                                                AgentConfigDto config,
                                                                String ownerId,
                                                                String tenantId) {
        DownstreamAgentCreateRequest payload = new DownstreamAgentCreateRequest(
                request.getName(),
                agentSpec.getGoal(),
                request.getDescription(),
                ownerId,
                tenantId,
                agentSpec.getGoal(),
                buildDownstreamTools(agentSpec.getAllowedTools()),
                buildDownstreamPolicies(agentSpec.getAllowedTools()),
                6,
                config != null ? config.temperature() : null,
                config != null ? config.ragScope() : List.of(),
                config != null ? config.reasoningStyle() : null,
                config != null ? config.retrieverType() : null,
                config != null ? config.retrieverK() : null,
                config != null ? config.executionMode() : null,
                config != null ? config.permissions() : List.of()
        );

        return downstreamAgentService.createAgent(payload);
    }

    private List<DownstreamTool> buildDownstreamTools(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return List.of();
        }

        return toolNames.stream()
                .map(name -> {
                    MCPTool tool = toolRegistry.getTool(name).orElse(null);
                    String toolName = simplifyToolName(name);
                    String description = tool != null ? tool.getDescription() : null;
                    boolean requiresApproval = requiresApproval(toolName);
                    return new DownstreamTool(toolName, description, requiresApproval);
                })
                .toList();
    }

    private List<DownstreamPolicy> buildDownstreamPolicies(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return List.of();
        }

        List<String> approvalTools = toolNames.stream()
                .map(this::simplifyToolName)
                .filter(this::requiresApproval)
                .toList();

        if (approvalTools.isEmpty()) {
            return List.of();
        }

        return List.of(new DownstreamPolicy(
                "approval_required",
                "tool-approval",
                Map.of("tools", approvalTools)
        ));
    }

    private boolean requiresApproval(String toolName) {
        String name = toolName.toLowerCase();
        return name.contains("post_") || name.contains("create") || name.contains("update") || name.contains("delete");
    }

    private String simplifyToolName(String fullName) {
        String name = fullName;
        if (name.startsWith("mcp_")) {
            name = name.substring(4);
        }
        String[] knownTools = {
                "call_jira_rest_api", "add_labels", "get_field_info",
                "search_confluence_pages", "get_confluence_page_by_id",
                "get_confluence_page_by_title", "get_confluence_page_by_url",
                "call_confluence_rest_api",
                "call_github_graphql_for_query", "get_pull_request_diff",
                "call_github_restapi_for_search", "call_github_graphql_for_mutation",
                "who_am_i", "get_person", "list_spaces", "get_space",
                "list_memberships", "list_messages", "get_message", "post_message",
                "get_context_around_message", "index_space_messages",
                "retrieve_relevant", "ask_space"
        };
        for (String tool : knownTools) {
            if (name.endsWith(tool)) {
                return tool;
            }
        }
        return name;
    }

    private String resolveOwnerId(CreateAgentRequest request) {
        String ownerId = request.getOwnerId();
        return (ownerId == null || ownerId.isBlank())
                ? java.util.UUID.randomUUID().toString()
                : ownerId;
    }

    private String resolveTenantId(CreateAgentRequest request) {
        String tenantId = request.getTenantId();
        return (tenantId == null || tenantId.isBlank())
                ? java.util.UUID.randomUUID().toString()
                : tenantId;
    }

    private String deriveStatus(Map<String, Object> sample) {
        if (sample == null || sample.isEmpty()) {
            return "empty";
        }
        Object error = sample.get("error");
        if (error != null) {
            return String.valueOf(error);
        }
        Object size = sample.get("sizeBytes");
        return size != null ? "ok(sizeBytes=" + size + ")" : "ok";
    }

    private String buildFetchMessage(Map<String, String> status) {
        if (status.isEmpty()) {
            return "No MCP sources fetched";
        }
        StringBuilder builder = new StringBuilder("MCP fetch status: ");
        status.forEach((key, value) -> builder.append(key).append("=").append(value).append("; "));
        return builder.toString().trim();
    }

    private record DecisionFetchResult(
            Map<String, Object> data,
            Map<String, String> status,
            String message
    ) {}
}
