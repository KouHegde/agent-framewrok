package com.agentframework.controller;

import com.agentframework.common.dto.AgentDto;
import com.agentframework.dto.*;
import com.agentframework.facade.AgentFacade;
import com.agentframework.service.AgentExecutorService;
import com.agentframework.service.MetaAgentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Agent operations.
 * 
 * New Agent Management Endpoints:
 * - POST /api/agents: Create agent (persisted to DB)
 * - GET /api/agents: List all agents
 * - GET /api/agents/{id}: Get agent by ID
 * - POST /api/agents/{id}/run: Run agent by ID
 * - DELETE /api/agents/{id}: Delete agent
 * 
 * Legacy Endpoints (still supported):
 * - POST /api/create-agent: Create agent spec (not persisted)
 * - POST /api/run-agent: Execute an agent with a spec
 * - POST /api/create-and-run-agent: Create and execute in one call
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AgentController {

    private final MetaAgentService metaAgentService;
    private final AgentExecutorService agentExecutorService;
    private final com.agentframework.registry.MCPToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    
    // Optional - injected if database is configured
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.agentframework.data.facade.AgentDataFacade agentDataFacade;

    // Optional - facade wrapper around data + spec creation
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AgentFacade agentFacade;

    /**
     * LEGACY: Create an agent specification from name and description (not persisted).
     * The MetaAgentService analyzes the description and identifies
     * which MCP tools are needed.
     * 
     * For persistent agents, use POST /api/agents instead.
     */
    @PostMapping("/create-agent")
    public ResponseEntity<AgentSpec> createAgentSpec(@Valid @RequestBody CreateAgentRequest request) {
        log.info("Received create-agent request for: {}", request.getName());

        AgentSpec agentSpec = metaAgentService.buildAgentSpec(request);

        log.info("Generated agent spec: {} with tools: {}",
                agentSpec.getAgentName(),
                agentSpec.getAllowedTools());

        return ResponseEntity.ok(agentSpec);
    }

    /**
     * Run an agent with a given spec and query.
     */
    @PostMapping("/run-agent")
    public ResponseEntity<AgentExecutionResponse> runAgent(@Valid @RequestBody AgentExecutionRequest request) {
        log.info("Received run-agent request for: {}", request.getAgentSpec().getAgentName());
        log.info("Query: {}", request.getQuery());

        AgentExecutionResponse response = agentExecutorService.executeAgent(request);

        log.info("Agent execution completed. Status: {}, Tools used: {}",
                response.getStatus(),
                response.getToolsUsed().size());

        return ResponseEntity.ok(response);
    }

    /**
     * Combined endpoint: Create agent spec and run it in one call.
     * Useful for quick testing and simple use cases.
     *
     * Request body:
     * {
     *   "name": "Agent Name",
     *   "description": "What the agent should do",
     *   "query": "Optional - specific query (defaults to description)",
     *   "inputs": { "spaceId": "...", "text": "..." }
     * }
     */
    @PostMapping("/create-and-run-agent")
    public ResponseEntity<AgentExecutionResponse> createAndRunAgent(
            @RequestBody CreateAndRunRequest request) {

        log.info("Received create-and-run-agent request for: {}", request.getName());

        // Step 1: Create agent spec
        CreateAgentRequest createRequest = new CreateAgentRequest();
        createRequest.setName(request.getName());
        createRequest.setDescription(request.getDescription());
        AgentSpec agentSpec = metaAgentService.buildAgentSpec(createRequest);

        // Step 2: Execute agent (use description as query if query not provided)
        AgentExecutionRequest execRequest = new AgentExecutionRequest();
        execRequest.setAgentSpec(agentSpec);
        String query = request.getQuery();
        execRequest.setQuery(query != null && !query.isBlank() ? query : request.getDescription());
        execRequest.setInputs(request.getInputs());

        AgentExecutionResponse response = agentExecutorService.executeAgent(execRequest);

        return ResponseEntity.ok(response);
    }

    /**
     * Request DTO for create-and-run-agent endpoint
     */
    @lombok.Data
    public static class CreateAndRunRequest {
        private String name;
        private String description;
        private String query;  // Optional - defaults to description
        private Map<String, Object> inputs;  // Optional - runtime inputs
    }

    // =====================================================
    // NEW AGENT MANAGEMENT ENDPOINTS (with Database)
    // =====================================================

    /**
     * Create a new agent (persisted to database).
     * 
     * Flow:
     * 1. Build agent spec from MCP tools (to get allowed_tools)
     * 2. Check if agent with SAME TOOLS already exists (deduplication by functionality)
     * 3. If exists, return existing agent
     * 4. If not, call Python AgentBrain service (if enabled)
     * 5. Store new agent in DB
     * 
     * Request:
     * POST /api/agents
     * { "name": "Jira Issue Fetcher", "description": "Fetch Jira issues", "owner_id": "user-123", "tenant_id": "tenant-xyz" }
     * 
     * Response:
     * { "agentId": "uuid", "name": "...", "status": "created" or "existing", ... }
     */
    @PostMapping("/agents")
    public ResponseEntity<AgentCreateResponse> createAgent(@RequestBody CreateAgentRequest request) {
        log.info("Creating agent: {}", request.getName());

        if (agentFacade == null) {
            throw new IllegalStateException("Agent facade not configured");
        }

        var result = agentFacade.createAgent(request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.response());
    }

    /**
     * List all agents.
     * 
     * GET /api/agents
     */
    @GetMapping("/agents")
    public ResponseEntity<?> listAgents() {
        if (agentFacade != null) {
            var agents = agentFacade.listAgents();
            List<AgentSummaryResponse> summaries = agents.stream()
                    .map(this::toSummary)
                    .toList();
            return ResponseEntity.ok(new AgentListResponse(summaries.size(), summaries, null));
        }

        if (agentDataFacade == null) {
            return ResponseEntity.ok(new AgentListResponse(0, List.of(), "Database not configured"));
        }

        var agents = agentDataFacade.findAll();
        List<AgentSummaryResponse> summaries = agents.stream()
                .map(this::toSummary)
                .toList();
        return ResponseEntity.ok(new AgentListResponse(summaries.size(), summaries, null));
    }

    /**
     * Get agent by ID.
     * 
     * GET /api/agents/{id}
     */
    @GetMapping("/agents/{id}")
    public ResponseEntity<?> getAgent(@PathVariable("id") UUID agentId) {
        if (agentFacade != null) {
            return agentFacade.findAgentById(agentId)
                    .map(agent -> ResponseEntity.ok(toDetail(agent)))
                    .orElse(ResponseEntity.notFound().build());
        }

        if (agentDataFacade == null) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Database not configured", null));
        }

        return agentDataFacade.findById(agentId)
                .map(agent -> ResponseEntity.ok(toDetail(agent)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get agent config by ID.
     *
     * GET /api/agents/{id}/config
     */
    @GetMapping("/agents/{id}/config")
    public ResponseEntity<?> getAgentConfig(@PathVariable("id") UUID agentId) {
        var agentOpt = agentFacade != null
                ? agentFacade.findAgentById(agentId)
                : agentDataFacade.findById(agentId);

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
     * Run an agent by ID.
     * 
     * POST /api/agents/{id}/run
     * { "query": "Get details for CAI-6675", "inputs": { ... } }
     */
    @PostMapping("/agents/{id}/run")
    public ResponseEntity<?> runAgentById(
            @PathVariable("id") UUID agentId,
            @RequestBody(required = false) RunAgentRequest request) {

        if (agentFacade == null && agentDataFacade == null) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Database not configured", null));
        }

        var agentOpt = agentFacade != null
                ? agentFacade.findAgentById(agentId)
                : agentDataFacade.findById(agentId);
        if (agentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var agent = agentOpt.get();
        log.info("Running agent: {} ({})", agent.name(), agentId);

        try {
            // Parse stored AgentSpec
            AgentSpec agentSpec = objectMapper.readValue(agent.agentSpec(), AgentSpec.class);

            // Build execution request
            AgentExecutionRequest execRequest = new AgentExecutionRequest();
            execRequest.setAgentSpec(agentSpec);
            
            String query = request != null && request.getQuery() != null ? request.getQuery() : agent.description();
            execRequest.setQuery(query);
            
            if (request != null && request.getInputs() != null) {
                execRequest.setInputs(request.getInputs());
            }

            // Execute
            AgentExecutionResponse response = agentExecutorService.executeAgent(execRequest);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to run agent: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(
                    new ErrorResponse("Failed to run agent", e.getMessage())
            );
        }
    }

    /**
     * Delete an agent by ID.
     * 
     * DELETE /api/agents/{id}
     */
    @DeleteMapping("/agents/{id}")
    public ResponseEntity<?> deleteAgent(@PathVariable("id") UUID agentId) {
        if (agentFacade != null) {
            try {
                agentFacade.deleteAgent(agentId);
                return ResponseEntity.ok(new AgentDeleteResponse("deleted", agentId.toString()));
            } catch (Exception e) {
                return ResponseEntity.notFound().build();
            }
        }

        if (agentDataFacade == null) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Database not configured", null));
        }

        try {
            agentDataFacade.deleteAgent(agentId);
            return ResponseEntity.ok(new AgentDeleteResponse("deleted", agentId.toString()));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Request DTO for run agent by ID
     */
    @lombok.Data
    public static class RunAgentRequest {
        private String query;
        private Map<String, Object> inputs;
    }

    // =====================================================
    // UTILITY ENDPOINTS
    // =====================================================

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse(
                "UP",
                "agent-framework",
                agentDataFacade != null ? "connected" : "not_configured"
        ));
    }

    /**
     * Get all available MCP tools
     */
    @GetMapping("/tools")
    public ResponseEntity<ToolsResponse> getTools() {
        var tools = toolRegistry.getAllTools();
        var byCategory = toolRegistry.getToolsByCategories();

        List<ToolCategoryResponse> categories = byCategory.entrySet().stream()
                .map(entry -> new ToolCategoryResponse(
                        entry.getKey(),
                        entry.getValue().stream()
                                .map(t -> new ToolSummaryResponse(
                                        t.getName(),
                                        t.getDescription(),
                                        t.getCapabilities()
                                ))
                                .toList()
                ))
                .toList();

        return ResponseEntity.ok(new ToolsResponse(tools.size(), categories));
    }

    private AgentSummaryResponse toSummary(AgentDto agent) {
        return new AgentSummaryResponse(
                agent.id().toString(),
                agent.name(),
                agent.description() != null ? agent.description() : "",
                agent.mcpServerNames(),
                agent.createdAt().toString()
        );
    }

    private AgentDetailResponse toDetail(AgentDto agent) {
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
                new AgentRunExample("POST", runEndpoint, Map.of("query", "Your query here"))
        );
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
