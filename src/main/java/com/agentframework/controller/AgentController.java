package com.agentframework.controller;

import com.agentframework.dto.AgentExecutionRequest;
import com.agentframework.dto.AgentExecutionResponse;
import com.agentframework.dto.AgentSpec;
import com.agentframework.dto.CreateAgentRequest;
import com.agentframework.service.AgentExecutorService;
import com.agentframework.service.MetaAgentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<Map<String, Object>> createAgent(@RequestBody CreateAgentRequest request) {
        log.info("Creating agent: {}", request.getName());

        // Step 1: Build agent spec from MCP tools
        log.info("Building agent spec for: {}", request.getName());
        AgentSpec agentSpec = metaAgentService.buildAgentSpec(request);
        log.info("Selected MCP tools: {}", agentSpec.getAllowedTools());

        // Create deduplication key: tools + mode + permissions
        // Normalize tools: lowercase, trim, filter empty, distinct, sorted
        List<String> normalizedTools = agentSpec.getAllowedTools().stream()
                .map(String::toLowerCase)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted()
                .toList();
        String toolsKey = String.join(",", normalizedTools);
        
        // Build compound dedup key: tools|mode|permissions
        String executionMode = agentSpec.getExecutionMode() != null ? agentSpec.getExecutionMode() : "static";
        String permissions = agentSpec.getPermissions() != null && !agentSpec.getPermissions().isEmpty() 
                ? String.join(",", agentSpec.getPermissions()) 
                : "read_only";
        String allowedToolsKey = String.join("|", toolsKey, executionMode, permissions);
        log.info("Deduplication key (tools|mode|permissions): {}", allowedToolsKey);

        // Step 2: Check if agent with SAME capabilities already exists
        if (agentDataFacade != null && agentDataFacade.existsByAllowedTools(allowedToolsKey)) {
            log.info("Agent with same capabilities already exists. Returning existing agent.");
            var existingAgent = agentDataFacade.findByAllowedTools(allowedToolsKey);
            if (existingAgent.isPresent()) {
                var agentDto = existingAgent.get();
                String agentId = agentDto.id().toString();
                Map<String, Object> response = new java.util.HashMap<>();
                response.put("agentId", agentId);
                response.put("name", agentDto.name());
                response.put("description", agentDto.description() != null ? agentDto.description() : "");
                response.put("status", "existing");
                response.put("message", "Found existing agent with same capabilities (tools + mode + permissions)");
                response.put("allowedTools", normalizedTools);
                response.put("executionMode", executionMode);
                response.put("permissions", permissions);
                response.put("mcpServers", agentDto.mcpServerNames());
                response.put("createdAt", agentDto.createdAt().toString());
                
                // Include run endpoint for easy execution
                response.put("runEndpoint", "/api/agents/" + agentId + "/run");
                response.put("runExample", Map.of(
                    "method", "POST",
                    "url", "/api/agents/" + agentId + "/run",
                    "body", Map.of("query", "Your query here")
                ));
                
                return ResponseEntity.ok(response);
            }
        }

        // Step 3: Check if database is available
        if (agentDataFacade == null) {
            log.warn("Database not configured. Agent will not be persisted.");
            return ResponseEntity.ok(Map.of(
                    "status", "created_temp",
                    "message", "Database not configured. Agent not persisted.",
                    "agentSpec", agentSpec
            ));
        }

        // Step 4: Save to database
        try {
            String agentSpecJson = objectMapper.writeValueAsString(agentSpec);
            
            // Extract MCP server names from tool names
            List<String> mcpServers = agentSpec.getAllowedTools().stream()
                    .map(tool -> {
                        // Tool format: mcp_jira_call_jira_rest_api -> jira
                        if (tool.startsWith("mcp_")) {
                            String[] parts = tool.substring(4).split("_", 2);
                            return parts[0];
                        }
                        return tool;
                    })
                    .distinct()
                    .toList();

            var agentDto = agentDataFacade.getOrCreateAgent(
                    request.getName(),
                    request.getDescription(),
                    agentSpec.getGoal(),
                    allowedToolsKey,
                    agentSpecJson,
                    request.getOwnerId(),
                    request.getTenantId(),
                    mcpServers
            );

            log.info("Agent created with ID: {}", agentDto.id());

            // Build response
            String agentId = agentDto.id().toString();
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("agentId", agentId);
            response.put("name", agentDto.name());
            response.put("description", agentDto.description() != null ? agentDto.description() : "");
            response.put("status", "created");
            response.put("allowedTools", normalizedTools);
            response.put("executionMode", executionMode);
            response.put("permissions", permissions);
            response.put("mcpServers", agentDto.mcpServerNames());
            response.put("createdAt", agentDto.createdAt().toString());
            
            // Include run endpoint for easy execution
            response.put("runEndpoint", "/api/agents/" + agentId + "/run");
            response.put("runExample", Map.of(
                "method", "POST",
                "url", "/api/agents/" + agentId + "/run",
                "body", Map.of("query", "Your query here")
            ));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to save agent: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to save agent",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * List all agents.
     * 
     * GET /api/agents
     */
    @GetMapping("/agents")
    public ResponseEntity<?> listAgents() {
        if (agentDataFacade == null) {
            return ResponseEntity.ok(Map.of(
                    "message", "Database not configured",
                    "agents", List.of()
            ));
        }

        var agents = agentDataFacade.findAll();
        return ResponseEntity.ok(Map.of(
                "total", agents.size(),
                "agents", agents.stream().map(a -> Map.of(
                        "agentId", a.id().toString(),
                        "name", a.name(),
                        "description", a.description() != null ? a.description() : "",
                        "mcpServers", a.mcpServerNames(),
                        "createdAt", a.createdAt().toString()
                )).toList()
        ));
    }

    /**
     * Get agent by ID.
     * 
     * GET /api/agents/{id}
     */
    @GetMapping("/agents/{id}")
    public ResponseEntity<?> getAgent(@PathVariable("id") UUID agentId) {
        if (agentDataFacade == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Database not configured"));
        }

        return agentDataFacade.findById(agentId)
                .map(agent -> ResponseEntity.ok(Map.of(
                        "agentId", agent.id().toString(),
                        "name", agent.name(),
                        "description", agent.description() != null ? agent.description() : "",
                        "agentSpec", agent.agentSpec() != null ? agent.agentSpec() : "",
                        "mcpServers", agent.mcpServerNames(),
                        "createdAt", agent.createdAt().toString(),
                        "updatedAt", agent.updatedAt().toString()
                )))
                .orElse(ResponseEntity.notFound().build());
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

        if (agentDataFacade == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Database not configured"));
        }

        var agentOpt = agentDataFacade.findById(agentId);
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
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to run agent",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Delete an agent by ID.
     * 
     * DELETE /api/agents/{id}
     */
    @DeleteMapping("/agents/{id}")
    public ResponseEntity<?> deleteAgent(@PathVariable("id") UUID agentId) {
        if (agentDataFacade == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Database not configured"));
        }

        try {
            agentDataFacade.deleteAgent(agentId);
            return ResponseEntity.ok(Map.of(
                    "status", "deleted",
                    "agentId", agentId.toString()
            ));
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
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "agent-framework",
                "database", agentDataFacade != null ? "connected" : "not_configured"
        ));
    }

    /**
     * Get all available MCP tools
     */
    @GetMapping("/tools")
    public ResponseEntity<Map<String, Object>> getTools() {
        var tools = toolRegistry.getAllTools();
        var byCategory = toolRegistry.getToolsByCategories();
        
        return ResponseEntity.ok(Map.of(
                "total", tools.size(),
                "byCategory", byCategory.entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue().stream()
                                        .map(t -> Map.of(
                                                "name", t.getName(),
                                                "description", t.getDescription(),
                                                "capabilities", t.getCapabilities()
                                        ))
                                        .collect(java.util.stream.Collectors.toList())
                        ))
        ));
    }
}
