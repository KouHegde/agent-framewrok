package com.agentframework.controller;

import com.agentframework.common.dto.AgentDto;
import com.agentframework.dto.AgentConfigResponse;
import com.agentframework.dto.AgentCreateResponse;
import com.agentframework.dto.AgentDeleteResponse;
import com.agentframework.dto.AgentDetailResponse;
import com.agentframework.dto.AgentListResponse;
import com.agentframework.dto.AgentRunExample;
import com.agentframework.dto.AgentSpec;
import com.agentframework.dto.AgentSummaryResponse;
import com.agentframework.dto.CreateAgentRequest;
import com.agentframework.dto.DownstreamAgentDetailResponse;
import com.agentframework.dto.DownstreamAgentListResponse;
import com.agentframework.dto.ErrorResponse;
import com.agentframework.facade.AgentFacade;
import com.agentframework.service.DownstreamAgentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
@Tag(name = "Agents", description = "Agent CRUD operations")
public class AgentCrudController {

    private final AgentFacade agentFacade;
    private final ObjectMapper objectMapper;
    private final DownstreamAgentService downstreamAgentService;

    /**
     * Create a new agent (persisted to database).
     * Requires: agents:write scope
     */
    @Operation(summary = "Create agent", description = "Create a new AI agent. Requires agents:write scope.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Agent created successfully"),
            @ApiResponse(responseCode = "200", description = "Agent already exists (returned existing)"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    @PostMapping
    @PreAuthorize("@scopeChecker.hasScope(authentication, 'agents:write')")
    public ResponseEntity<AgentCreateResponse> createAgent(@RequestBody CreateAgentRequest request) {
        log.info("Creating agent: {}", request.getName());
        var result = agentFacade.createAgent(request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.response());
    }

    /**
     * List all agents.
     * Requires: agents:read scope
     */
    @Operation(summary = "List agents", description = "List all agents. Requires agents:read scope.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Agents retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    @GetMapping
    @PreAuthorize("@scopeChecker.hasScope(authentication, 'agents:read')")
    public ResponseEntity<AgentListResponse> listAgents(
            @RequestParam(value = "owner_id", required = false) String ownerId,
            @RequestParam(value = "tenant_id", required = false) String tenantId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "offset", required = false) Integer offset) {
        var agents = agentFacade.listAgents();
        var downstream = fetchDownstreamList(ownerId, tenantId, status, limit, offset);
        var downstreamById = indexDownstreamById(downstream);

        List<AgentSummaryResponse> summaries = agents.stream()
                .map(agent -> toSummary(agent, downstreamById))
                .toList();
        return ResponseEntity.ok(new AgentListResponse(summaries.size(), summaries, null));
    }

    /**
     * Get agent by ID.
     * Requires: agents:read scope
     */
    @Operation(summary = "Get agent by ID", description = "Get agent details. Requires agents:read scope.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Agent retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Agent not found"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    @GetMapping("/{id}")
    @PreAuthorize("@scopeChecker.hasScope(authentication, 'agents:read')")
    public ResponseEntity<?> getAgent(
            @Parameter(description = "Agent ID") @PathVariable("id") UUID agentId) {
        return agentFacade.findAgentById(agentId)
                .map(agent -> ResponseEntity.ok(toDetail(agent, fetchDownstream(agent))))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get agent config by ID.
     * Requires: agents:read scope
     */
    @Operation(summary = "Get agent configuration", description = "Get agent configuration details. Requires agents:read scope.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Configuration retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Agent not found"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    @GetMapping("/{id}/config")
    @PreAuthorize("@scopeChecker.hasScope(authentication, 'agents:read')")
    public ResponseEntity<?> getAgentConfig(
            @Parameter(description = "Agent ID") @PathVariable("id") UUID agentId) {
        var agentOpt = agentFacade.findAgentById(agentId);
        if (agentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var agent = agentOpt.get();
        return ResponseEntity.ok(new AgentConfigResponse(
                agent.id().toString(),
                splitCsv(agent.ragScope()),
                agent.reasoningStyle(),
                agent.temperature(),
                agent.retrieverType(),
                agent.retrieverK(),
                agent.executionMode(),
                splitCsv(agent.permissions())
        ));
    }

    /**
     * Delete an agent by ID.
     * Requires: agents:delete scope
     */
    @Operation(summary = "Delete agent", description = "Delete an agent. Requires agents:delete scope (ADMIN only).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Agent deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Agent not found"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("@scopeChecker.hasScope(authentication, 'agents:delete')")
    public ResponseEntity<?> deleteAgent(
            @Parameter(description = "Agent ID") @PathVariable("id") UUID agentId) {
        try {
            agentFacade.deleteAgent(agentId);
            return ResponseEntity.ok(new AgentDeleteResponse("deleted", agentId.toString()));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    private AgentSummaryResponse toSummary(AgentDto agent,
                                           Map<String, DownstreamAgentDetailResponse> downstreamById) {
        DownstreamAgentDetailResponse downstream = null;
        if (agent.downstreamAgentId() != null) {
            downstream = downstreamById.get(agent.downstreamAgentId());
        }
        return new AgentSummaryResponse(
                agent.id().toString(),
                agent.name(),
                agent.description() != null ? agent.description() : "",
                agent.mcpServerNames(),
                agent.createdAt().toString(),
                agent.downstreamStatus(),
                agent.downstreamAgentId(),
                downstream
        );
    }

    private AgentDetailResponse toDetail(AgentDto agent,
                                         DownstreamAgentDetailResponse downstream) {
        String agentId = agent.id().toString();
        String runEndpoint = "/api/agents/" + agentId + "/run";

        return new AgentDetailResponse(
                agentId,
                agent.name(),
                agent.description() != null ? agent.description() : "",
                parseAgentSpec(agent.agentSpec()),
                agent.mcpServerNames(),
                agent.createdAt().toString(),
                agent.updatedAt().toString(),
                runEndpoint,
                new AgentRunExample("POST", runEndpoint, Map.of("query", "Your query here")),
                downstream
        );
    }

    private DownstreamAgentDetailResponse fetchDownstream(AgentDto agent) {
        if (agent.downstreamAgentId() == null) {
            return null;
        }
        try {
            return downstreamAgentService.getAgent(agent.downstreamAgentId());
        } catch (Exception e) {
            log.warn("Failed to fetch downstream agent {}: {}", agent.downstreamAgentId(), e.getMessage());
            return null;
        }
    }

    private DownstreamAgentListResponse fetchDownstreamList(String ownerId,
                                                            String tenantId,
                                                            String status,
                                                            Integer limit,
                                                            Integer offset) {
        try {
            Integer resolvedLimit = limit != null ? limit : 10;
            Integer resolvedOffset = offset != null ? offset : 0;
            return downstreamAgentService.listAgents(ownerId, tenantId, status, resolvedLimit, resolvedOffset);
        } catch (Exception e) {
            log.warn("Failed to fetch downstream agent list: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, DownstreamAgentDetailResponse> indexDownstreamById(DownstreamAgentListResponse response) {
        if (response == null || response.getAgents() == null) {
            return Map.of();
        }
        return response.getAgents().stream()
                .filter(agent -> agent.getId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        DownstreamAgentDetailResponse::getId,
                        agent -> agent
                ));
    }

    private AgentSpec parseAgentSpec(String agentSpecJson) {
        if (agentSpecJson == null || agentSpecJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(agentSpecJson, AgentSpec.class);
        } catch (Exception e) {
            log.warn("Failed to parse agentSpec JSON for response: {}", e.getMessage());
            return null;
        }
    }

    private List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
