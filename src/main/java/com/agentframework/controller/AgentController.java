package com.agentframework.controller;

import com.agentframework.dto.AgentExecutionRequest;
import com.agentframework.dto.AgentExecutionResponse;
import com.agentframework.dto.AgentSpec;
import com.agentframework.dto.CreateAgentRequest;
import com.agentframework.service.AgentExecutorService;
import com.agentframework.service.MetaAgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for Agent operations.
 *
 * Endpoints:
 * - POST /api/create-agent: Create agent spec from name + description
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

    /**
     * Create an agent specification from name and description.
     * The MetaAgentService analyzes the description and identifies
     * which MCP tools are needed.
     */
    @PostMapping("/create-agent")
    public ResponseEntity<AgentSpec> createAgent(@Valid @RequestBody CreateAgentRequest request) {
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

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "agent-framework"
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
