package com.agentframework.service;

import com.agentframework.dto.AgentExecutionRequest;
import com.agentframework.dto.AgentExecutionResponse;
import com.agentframework.dto.AgentExecutionResponse.ToolExecutionResult;
import com.agentframework.dto.AgentSpec;
import com.agentframework.registry.MCPTool;
import com.agentframework.registry.MCPToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import io.netty.channel.ChannelOption;
import jakarta.annotation.PostConstruct;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent Executor Service that runs agents by calling MCP Servers.
 * 
 * MCP Servers expose HTTP endpoints with JSON-RPC protocol.
 * 
 * Request Format (MCP JSON-RPC):
 * {
 *   "jsonrpc": "2.0",
 *   "id": 1,
 *   "method": "tools/call",
 *   "params": {
 *     "name": "call_jira_rest_api",
 *     "arguments": {
 *       "endpoint": "issue/PROJ-123",
 *       "method": "GET"
 *     }
 *   }
 * }
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentExecutorService {

    private final MCPToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    // Request ID counter for JSON-RPC
    private final AtomicLong requestIdCounter = new AtomicLong(1);

    // MCP Server URLs (HTTP transport)
    @Value("${mcp.jira.url:}")
    private String jiraMcpUrl;

    @Value("${mcp.confluence.url:}")
    private String confluenceMcpUrl;

    @Value("${mcp.github.url:}")
    private String githubMcpUrl;

    // Authentication tokens
    @Value("${mcp.jira.pat-token:}")
    private String jiraPatToken;

    @Value("${mcp.confluence.pat-token:}")
    private String confluencePatToken;

    @Value("${mcp.github.pat-token:}")
    private String githubPatToken;

    private WebClient jiraMcpClient;
    private WebClient confluenceMcpClient;
    private WebClient githubMcpClient;

    @PostConstruct
    public void init() {
        // Initialize Jira MCP Client
        if (jiraMcpUrl != null && !jiraMcpUrl.isBlank()) {
            jiraMcpClient = createMcpClient(jiraMcpUrl, jiraPatToken);
            log.info("Jira MCP Client initialized: {}", jiraMcpUrl);
        } else {
            log.warn("Jira MCP Server not configured. Set MCP_JIRA_URL");
        }

        // Initialize Confluence MCP Client
        if (confluenceMcpUrl != null && !confluenceMcpUrl.isBlank()) {
            confluenceMcpClient = createMcpClient(confluenceMcpUrl, confluencePatToken);
            log.info("Confluence MCP Client initialized: {}", confluenceMcpUrl);
        } else {
            log.warn("Confluence MCP Server not configured. Set MCP_CONFLUENCE_URL");
        }

        // Initialize GitHub MCP Client
        if (githubMcpUrl != null && !githubMcpUrl.isBlank()) {
            githubMcpClient = createMcpClient(githubMcpUrl, githubPatToken);
            log.info("GitHub MCP Client initialized: {}", githubMcpUrl);
        } else {
            log.warn("GitHub MCP Server not configured. Set MCP_GITHUB_URL");
        }
    }

    /**
     * Create a WebClient for MCP Server communication with redirect support
     */
    private WebClient createMcpClient(String baseUrl, String patToken) {
        // Configure HttpClient to follow redirects
        HttpClient httpClient = HttpClient.create()
                .followRedirect(true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .responseTimeout(Duration.ofSeconds(30));

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                // MCP servers require both application/json and text/event-stream in Accept header
                .defaultHeader(HttpHeaders.ACCEPT, "application/json, text/event-stream");

        // Add authentication if token provided
        if (patToken != null && !patToken.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + patToken);
            builder.defaultHeader("X-JIRA-TOKEN", patToken); // Jira-specific header
        }

        return builder.build();
    }

    /**
     * Execute an agent with the given spec and user inputs
     */
    public AgentExecutionResponse executeAgent(AgentExecutionRequest request) {
        long startTime = System.currentTimeMillis();
        AgentSpec spec = request.getAgentSpec();
        
        log.info("Executing agent: {} with query: {}", spec.getAgentName(), request.getQuery());
        log.info("Allowed tools: {}", spec.getAllowedTools());
        log.info("User inputs: {}", request.getInputs());

        List<ToolExecutionResult> toolResults = new ArrayList<>();
        StringBuilder aggregatedResult = new StringBuilder();
        String error = null;

        try {
            // Execute each allowed tool via MCP
            for (String toolName : spec.getAllowedTools()) {
                Optional<MCPTool> toolOpt = toolRegistry.getTool(toolName);
                
                if (toolOpt.isEmpty()) {
                    log.warn("Tool not found in registry: {}", toolName);
                    continue;
                }

                MCPTool tool = toolOpt.get();
                log.info("Executing MCP tool: {}", toolName);

                long toolStartTime = System.currentTimeMillis();
                
                try {
                    // Build tool arguments
                    Map<String, Object> arguments = buildToolArguments(tool, request);
                    
                    // Call MCP Server via JSON-RPC
                    Object result = callMcpTool(tool, arguments);
                    
                    long toolExecutionTime = System.currentTimeMillis() - toolStartTime;
                    
                    toolResults.add(ToolExecutionResult.builder()
                            .toolName(toolName)
                            .status("success")
                            .output(result)
                            .executionTimeMs(toolExecutionTime)
                            .build());
                    
                    // Aggregate results
                    if (result != null) {
                        aggregatedResult.append("=== Results from ").append(toolName).append(" ===\n");
                        aggregatedResult.append(formatResult(result)).append("\n\n");
                    }
                    
                } catch (Exception e) {
                    log.error("Error executing tool {}: {}", toolName, e.getMessage(), e);
                    toolResults.add(ToolExecutionResult.builder()
                            .toolName(toolName)
                            .status("error")
                            .output(Map.of("error", e.getMessage()))
                            .executionTimeMs(System.currentTimeMillis() - toolStartTime)
                            .build());
                    aggregatedResult.append("=== Error from ").append(toolName).append(" ===\n");
                    aggregatedResult.append(e.getMessage()).append("\n\n");
                }
            }

        } catch (Exception e) {
            log.error("Agent execution failed: {}", e.getMessage(), e);
            error = e.getMessage();
        }

        long totalTime = System.currentTimeMillis() - startTime;

        return AgentExecutionResponse.builder()
                .agentName(spec.getAgentName())
                .status(error == null ? "completed" : "failed")
                .result(aggregatedResult.toString())
                .toolsUsed(toolResults)
                .executionTimeMs(totalTime)
                .error(error)
                .build();
    }

    /**
     * Call an MCP tool using JSON-RPC protocol
     * 
     * Request format:
     * {
     *   "jsonrpc": "2.0",
     *   "id": 1,
     *   "method": "tools/call",
     *   "params": {
     *     "name": "tool_name",
     *     "arguments": {...}
     *   }
     * }
     */
    private Object callMcpTool(MCPTool tool, Map<String, Object> arguments) throws Exception {
        String category = tool.getCategory();
        String mcpToolName = extractMcpToolName(tool.getName());

        // Build JSON-RPC request
        Map<String, Object> jsonRpcRequest = new LinkedHashMap<>();
        jsonRpcRequest.put("jsonrpc", "2.0");
        jsonRpcRequest.put("id", requestIdCounter.getAndIncrement());
        jsonRpcRequest.put("method", "tools/call");
        jsonRpcRequest.put("params", Map.of(
                "name", mcpToolName,
                "arguments", arguments
        ));

        log.info("Calling MCP tool: {} with JSON-RPC request", mcpToolName);
        log.debug("Request body: {}", objectMapper.writeValueAsString(jsonRpcRequest));

        // Select the appropriate MCP client based on category
        WebClient mcpClient = getMcpClient(category);
        if (mcpClient == null) {
            return Map.of(
                    "error", "MCP Server not configured for category: " + category,
                    "message", "Please configure MCP_" + category.toUpperCase() + "_URL"
            );
        }

        try {
            // Send JSON-RPC request to MCP server
            String response = mcpClient.post()
                    .uri("")  // Base URL already contains the full endpoint
                    .bodyValue(jsonRpcRequest)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("MCP Response: {}", response);

            // Handle null or empty response
            if (response == null || response.isBlank()) {
                log.warn("MCP Server returned empty response");
                return Map.of(
                        "error", "MCP Server returned empty response",
                        "message", "The server may have redirected or returned no data"
                );
            }

            // Parse SSE format: "event: message\ndata: {json}"
            String jsonData = parseSseResponse(response);
            
            // Parse JSON-RPC response
            JsonNode responseNode = objectMapper.readTree(jsonData);

            // Check for JSON-RPC error
            if (responseNode.has("error")) {
                JsonNode errorNode = responseNode.get("error");
                return Map.of(
                        "error", errorNode.path("message").asText("Unknown error"),
                        "code", errorNode.path("code").asInt(-1)
                );
            }

            // Return the result
            if (responseNode.has("result")) {
                return responseNode.get("result");
            }

            return responseNode;

        } catch (WebClientResponseException e) {
            log.error("MCP Server error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return Map.of(
                    "error", "MCP Server error",
                    "status", e.getStatusCode().value(),
                    "message", e.getResponseBodyAsString()
            );
        }
    }

    /**
     * Get the appropriate MCP client based on tool category
     */
    private WebClient getMcpClient(String category) {
        switch (category.toLowerCase()) {
            case "jira":
                return jiraMcpClient;
            case "confluence":
                return confluenceMcpClient;
            case "github":
                return githubMcpClient;
            default:
                log.warn("Unknown MCP category: {}", category);
                return null;
        }
    }

    /**
     * Parse SSE (Server-Sent Events) response format
     * Format: "event: message\ndata: {json}\n\n"
     */
    private String parseSseResponse(String sseResponse) {
        if (sseResponse == null || sseResponse.isBlank()) {
            return "{}";
        }

        // Look for "data: " line and extract JSON
        String[] lines = sseResponse.split("\n");
        for (String line : lines) {
            if (line.startsWith("data: ")) {
                return line.substring(6).trim(); // Remove "data: " prefix
            }
        }

        // If no "data:" found, try to parse as plain JSON
        if (sseResponse.trim().startsWith("{")) {
            return sseResponse.trim();
        }

        log.warn("Could not parse SSE response: {}", sseResponse);
        return "{}";
    }

    /**
     * Extract the MCP tool name from the full registry name
     * e.g., "mcp_jira-sjc12_call_jira_rest_api" -> "call_jira_rest_api"
     */
    private String extractMcpToolName(String fullToolName) {
        // Known MCP tool names
        String[] knownTools = {
                "call_jira_rest_api", "add_labels", "get_field_info",
                "search_confluence_pages", "get_confluence_page_by_id",
                "get_confluence_page_by_title", "get_confluence_page_by_url",
                "call_confluence_rest_api",
                "call_github_graphql_for_query", "get_pull_request_diff",
                "call_github_restapi_for_search", "call_github_graphql_for_mutation"
        };

        for (String tool : knownTools) {
            if (fullToolName.endsWith(tool)) {
                return tool;
            }
        }

        // Fallback: try to extract from pattern
        String name = fullToolName;
        if (name.startsWith("mcp_")) {
            name = name.substring(4);
        }

        // Find where actual tool name starts
        for (String prefix : List.of("call_", "add_", "get_", "search_")) {
            int idx = name.indexOf(prefix);
            if (idx >= 0) {
                return name.substring(idx);
            }
        }

        return name;
    }

    /**
     * Build arguments for MCP tool call
     */
    private Map<String, Object> buildToolArguments(MCPTool tool, AgentExecutionRequest request) {
        Map<String, Object> arguments = new HashMap<>();
        Map<String, Object> userInputs = request.getInputs();
        String query = request.getQuery();

        // Copy user-provided inputs
        if (userInputs != null) {
            arguments.putAll(userInputs);
        }

        String toolName = tool.getName();
        String category = tool.getCategory();

        // Build category-specific arguments
        switch (category) {
            case "jira":
                buildJiraArguments(toolName, query, arguments);
                break;
            case "confluence":
                buildConfluenceArguments(toolName, query, arguments);
                break;
            case "github":
                buildGitHubArguments(toolName, query, arguments);
                break;
        }

        return arguments;
    }

    /**
     * Build Jira-specific arguments
     */
    private void buildJiraArguments(String toolName, String query, Map<String, Object> arguments) {
        // Auto-detect issue key from query
        String issueKey = extractIssueKey(query);

        if (toolName.contains("call_jira_rest_api")) {
            if (issueKey != null && !arguments.containsKey("endpoint")) {
                // Single issue fetch
                arguments.put("endpoint", "issue/" + issueKey);
                arguments.put("method", "GET");
            } else if (!arguments.containsKey("endpoint")) {
                // Search with JQL
                arguments.put("endpoint", "search");
                arguments.put("method", "GET");
                arguments.put("params", Map.of(
                        "jql", buildJql(query, arguments),
                        "maxResults", 50
                ));
            }
        } else if (toolName.contains("add_labels")) {
            if (issueKey != null) {
                arguments.putIfAbsent("issue_key", issueKey);
            }
        } else if (toolName.contains("get_field_info")) {
            // Field info doesn't need special handling
            if (!arguments.containsKey("search_term") && !arguments.containsKey("field_names")) {
                arguments.put("search_term", query);
            }
        }
    }

    /**
     * Build Confluence-specific arguments
     */
    private void buildConfluenceArguments(String toolName, String query, Map<String, Object> arguments) {
        if (toolName.contains("search_confluence_pages")) {
            if (!arguments.containsKey("cql_query")) {
                arguments.put("cql_query", "text ~ \"" + query + "\"");
            }
            arguments.putIfAbsent("limit", 25);
        }
        // Other Confluence tools require explicit inputs (page_id, space_key, etc.)
    }

    /**
     * Build GitHub-specific arguments
     */
    private void buildGitHubArguments(String toolName, String query, Map<String, Object> arguments) {
        if (toolName.contains("restapi_for_search")) {
            arguments.putIfAbsent("resource", "code");
            if (!arguments.containsKey("parameters")) {
                arguments.put("parameters", Map.of("q", query));
            }
        } else if (toolName.contains("graphql")) {
            // GraphQL requires explicit query
            if (!arguments.containsKey("graphql")) {
                arguments.put("graphql", query);
            }
        }
        // get_pull_request_diff requires explicit owner, repo, pull_number
    }

    /**
     * Build JQL from query text
     */
    private String buildJql(String query, Map<String, Object> arguments) {
        StringBuilder jql = new StringBuilder();

        // Add project filter if provided
        if (arguments.containsKey("project")) {
            jql.append("project = \"").append(arguments.get("project")).append("\" AND ");
        }

        // Text search
        jql.append("text ~ \"").append(query).append("\"");

        // Add status filter based on keywords
        String lowerQuery = query.toLowerCase();
        if (lowerQuery.contains("open") || lowerQuery.contains("active")) {
            jql.append(" AND statusCategory != Done");
        }
        if (lowerQuery.contains("bug")) {
            jql.append(" AND issuetype = Bug");
        }
        if (lowerQuery.contains("my") || lowerQuery.contains("assigned to me")) {
            jql.append(" AND assignee = currentUser()");
        }

        return jql.toString();
    }

    /**
     * Extract Jira issue key from query text (e.g., "CAI-6675")
     */
    private String extractIssueKey(String query) {
        if (query == null) return null;
        Pattern pattern = Pattern.compile("([A-Z]+-\\d+)");
        Matcher matcher = pattern.matcher(query.toUpperCase());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String formatResult(Object result) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            return result.toString();
        }
    }
}
