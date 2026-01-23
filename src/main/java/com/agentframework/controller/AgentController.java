package com.agentframework.controller;

import com.agentframework.dto.AgentExecutionRequest;
import com.agentframework.dto.AgentExecutionResponse;
import com.agentframework.dto.AgentSpec;
import com.agentframework.dto.CreateAgentRequest;
import com.agentframework.registry.MCPTool;
import com.agentframework.registry.MCPToolRegistry;
import com.agentframework.service.AgentExecutorService;
import com.agentframework.service.MetaAgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for Agent management and execution endpoints.
 * 
 * Endpoints:
 * - POST /create-agent: Create an agent specification from description
 * - POST /run-agent: Execute an agent with the given spec and inputs
 * - GET /tools: List available MCP tools
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AgentController {

    private final MetaAgentService metaAgentService;
    private final AgentExecutorService agentExecutorService;
    private final MCPToolRegistry mcpToolRegistry;

    /**
     * POST /create-agent
     * 
     * Creates an agent specification from a name and description.
     * The meta-agent analyzes the description and determines:
     * - The goal of the agent
     * - Which MCP tools the agent should use
     * - Execution mode (dynamic/static)
     * - Required permissions
     * - Expected input parameters
     *
     * @param request The agent creation request containing name and description
     * @return The generated agent specification
     */
    @PostMapping("/create-agent")
    public ResponseEntity<AgentSpec> createAgent(@Valid @RequestBody CreateAgentRequest request) {
        log.info("Received create-agent request for: {}", request.getName());
        
        AgentSpec agentSpec = metaAgentService.buildAgentSpec(request);
        
        log.info("Generated agent spec: {} with {} tools", 
                agentSpec.getAgentName(), 
                agentSpec.getAllowedTools().size());
        
        return ResponseEntity.ok(agentSpec);
    }

    /**
     * POST /run-agent
     * 
     * Executes an agent using the provided agent specification and user inputs.
     * The agent will call the appropriate MCP tools to complete the task.
     *
     * @param request The execution request containing agent spec, query, and inputs
     * @return The execution result with tool outputs
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
     * POST /create-and-run-agent
     * 
     * Convenience endpoint that creates an agent spec and immediately runs it.
     * Combines /create-agent and /run-agent into a single call.
     *
     * @param request Map containing name, description, query, and optional inputs
     * @return The execution result
     */
    @PostMapping("/create-and-run-agent")
    public ResponseEntity<AgentExecutionResponse> createAndRunAgent(@RequestBody Map<String, Object> request) {
        String name = (String) request.get("name");
        String description = (String) request.get("description");
        String query = (String) request.getOrDefault("query", description);
        @SuppressWarnings("unchecked")
        Map<String, Object> inputs = (Map<String, Object>) request.get("inputs");

        log.info("Received create-and-run-agent request for: {}", name);

        // Step 1: Create agent spec
        CreateAgentRequest createRequest = new CreateAgentRequest();
        createRequest.setName(name);
        createRequest.setDescription(description);
        AgentSpec agentSpec = metaAgentService.buildAgentSpec(createRequest);

        // Step 2: Run agent
        AgentExecutionRequest execRequest = new AgentExecutionRequest();
        execRequest.setAgentSpec(agentSpec);
        execRequest.setQuery(query);
        execRequest.setInputs(inputs);

        AgentExecutionResponse response = agentExecutorService.executeAgent(execRequest);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /tools
     * 
     * Lists all available MCP tools that agents can use.
     *
     * @return List of all registered MCP tools
     */
    @GetMapping("/tools")
    public ResponseEntity<List<MCPTool>> listTools() {
        return ResponseEntity.ok(mcpToolRegistry.getAllTools());
    }

    /**
     * GET /tools/categories
     * 
     * Lists available MCP tools grouped by category.
     *
     * @return Map of category names to tools
     */
    @GetMapping("/tools/categories")
    public ResponseEntity<Map<String, List<MCPTool>>> listToolsByCategory() {
        return ResponseEntity.ok(mcpToolRegistry.getToolsByCategories());
    }

    /**
     * GET /health
     * 
     * Health check endpoint.
     *
     * @return Health status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "service", "agent-framework",
                "version", "1.0.0"
        ));
    }
}
