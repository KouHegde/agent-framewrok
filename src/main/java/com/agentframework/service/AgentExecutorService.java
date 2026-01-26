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
    private final LLMService llmService;

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

    // Webex MCP Server (uses REST pattern, not JSON-RPC)
    @Value("${mcp.webex.url:}")
    private String webexMcpUrl;

    @Value("${mcp.webex.token:}")
    private String webexToken;

    private WebClient jiraMcpClient;
    private WebClient confluenceMcpClient;
    private WebClient githubMcpClient;
    private WebClient webexMcpClient;

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

        // Initialize Webex MCP Client (REST pattern with X-Webex-Token)
        if (webexMcpUrl != null && !webexMcpUrl.isBlank()) {
            webexMcpClient = createWebexMcpClient(webexMcpUrl, webexToken);
            log.info("Webex MCP Client initialized: {}", webexMcpUrl);
        } else {
            log.warn("Webex MCP Server not configured. Set MCP_WEBEX_URL");
        }
    }

    /**
     * Create a WebClient for MCP Server communication with redirect support (JSON-RPC pattern)
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
     * Create a WebClient for Webex MCP Server (REST pattern with X-Webex-Token)
     */
    private WebClient createWebexMcpClient(String baseUrl, String token) {
        HttpClient httpClient = HttpClient.create()
                .followRedirect(true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .responseTimeout(Duration.ofSeconds(30));

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        // Webex uses X-Webex-Token header
        if (token != null && !token.isBlank()) {
            builder.defaultHeader("X-Webex-Token", token);
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
     * Call an MCP tool - handles both JSON-RPC (Jira, Confluence, GitHub) and REST (Webex) patterns
     */
    private Object callMcpTool(MCPTool tool, Map<String, Object> arguments) throws Exception {
        String category = tool.getCategory();
        String mcpToolName = extractMcpToolName(tool.getName());

        // Webex uses REST pattern, others use JSON-RPC
        if ("webex".equals(category)) {
            return callWebexMcpTool(mcpToolName, arguments);
        }

        // Build JSON-RPC request for Jira/Confluence/GitHub
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
     * Call Webex MCP tool using REST pattern
     * Endpoint: /mcp/tools/{tool_name}
     * Auth: X-Webex-Token header
     */
    private Object callWebexMcpTool(String toolName, Map<String, Object> arguments) throws Exception {
        if (webexMcpClient == null) {
            return Map.of(
                    "error", "Webex MCP Server not configured",
                    "message", "Please configure MCP_WEBEX_URL and WEBEX_TOKEN"
            );
        }

        log.info("Calling Webex MCP tool: {} with REST request", toolName);
        log.debug("Arguments: {}", arguments);

        try {
            // Webex MCP uses REST pattern: POST /mcp/tools/{tool_name}
            String response = webexMcpClient.post()
                    .uri("/mcp/tools/" + toolName)
                    .bodyValue(arguments.isEmpty() ? Map.of() : arguments)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("Webex MCP Response: {}", response);

            if (response == null || response.isBlank()) {
                return Map.of("status", "success", "message", "Operation completed");
            }

            return objectMapper.readTree(response);

        } catch (WebClientResponseException e) {
            log.error("Webex MCP Server error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return Map.of(
                    "error", "Webex MCP Server error",
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
            case "webex":
                return webexMcpClient;
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
     * e.g., "mcp_webex_list_spaces" -> "list_spaces"
     */
    private String extractMcpToolName(String fullToolName) {
        // Known MCP tool names
        String[] knownTools = {
                // Jira
                "call_jira_rest_api", "add_labels", "get_field_info",
                // Confluence
                "search_confluence_pages", "get_confluence_page_by_id",
                "get_confluence_page_by_title", "get_confluence_page_by_url",
                "call_confluence_rest_api",
                // GitHub
                "call_github_graphql_for_query", "get_pull_request_diff",
                "call_github_restapi_for_search", "call_github_graphql_for_mutation",
                // Webex
                "who_am_i", "get_person", "list_spaces", "get_space", 
                "list_memberships", "list_messages", "get_message", "post_message",
                "get_context_around_message", "index_space_messages", 
                "retrieve_relevant", "ask_space"
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
     * 
     * Priority:
     * 1. User-provided explicit inputs (highest priority)
     * 2. LLM-powered argument building (if LLM enabled)
     * 3. Keyword-based pattern matching (fallback)
     */
    private Map<String, Object> buildToolArguments(MCPTool tool, AgentExecutionRequest request) {
        Map<String, Object> arguments = new HashMap<>();
        Map<String, Object> userInputs = request.getInputs();
        String query = request.getQuery();

        // Priority 1: User-provided explicit inputs (skip all detection)
        if (userInputs != null && !userInputs.isEmpty()) {
            log.info("Using user-provided explicit inputs");
            arguments.putAll(userInputs);
            return arguments;
        }

        String toolName = tool.getName();
        String category = tool.getCategory();

        // Priority 2: Try LLM-powered argument building
        if (llmService != null && llmService.isEnabled()) {
            log.info("Using LLM to build tool arguments for: {}", toolName);
            Map<String, Object> llmArgs = llmService.buildToolArguments(toolName, query, tool.getDescription());
            if (llmArgs != null && !llmArgs.isEmpty()) {
                log.info("LLM generated arguments: {}", llmArgs);
                return llmArgs;
            }
            log.warn("LLM argument building failed, falling back to keyword-based");
        }

        // Priority 3: Keyword-based pattern matching (fallback)
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
            case "webex":
                buildWebexArguments(toolName, query, arguments);
                break;
        }

        return arguments;
    }

    /**
     * Build Webex-specific arguments
     */
    private void buildWebexArguments(String toolName, String query, Map<String, Object> arguments) {
        if (toolName.contains("post_message")) {
            // Ensure markdown is set (Webex requires markdown, not text)
            if (!arguments.containsKey("markdown") && arguments.containsKey("text")) {
                arguments.put("markdown", arguments.get("text"));
            }
            if (!arguments.containsKey("markdown")) {
                arguments.put("markdown", query); // Use query as message if not provided
            }
        } else if (toolName.contains("list_spaces")) {
            arguments.putIfAbsent("max", 50);
        } else if (toolName.contains("list_messages")) {
            arguments.putIfAbsent("max", 20);
        } else if (toolName.contains("ask_space") || toolName.contains("retrieve_relevant")) {
            if (!arguments.containsKey("question")) {
                arguments.put("question", query);
            }
        }
    }

    /**
     * Build Jira-specific arguments
     */
    private void buildJiraArguments(String toolName, String query, Map<String, Object> arguments) {
        // Auto-detect issue key from query
        String issueKey = extractIssueKey(query);
        String lowerQuery = query.toLowerCase();

        if (toolName.contains("call_jira_rest_api")) {
            // Detect operation type from query
            if (lowerQuery.contains("add comment") || lowerQuery.contains("comment")) {
                // Add comment to issue
                if (issueKey != null) {
                    arguments.put("endpoint", "issue/" + issueKey + "/comment");
                    arguments.put("method", "POST");
                    
                    // Extract comment text from query
                    String commentText = extractCommentText(query);
                    arguments.put("data", Map.of("body", commentText));
                    log.info("Adding comment to {}: {}", issueKey, commentText);
                }
            } else if (lowerQuery.contains("update description") || lowerQuery.contains("change description")) {
                // Update issue description
                if (issueKey != null) {
                    arguments.put("endpoint", "issue/" + issueKey);
                    arguments.put("method", "PUT");
                    
                    String newDescription = extractDescriptionText(query);
                    arguments.put("data", Map.of("fields", Map.of("description", newDescription)));
                    log.info("Updating description for {}", issueKey);
                }
            } else if (lowerQuery.contains("change status") || lowerQuery.contains("transition") || 
                       lowerQuery.contains("move to") || lowerQuery.contains("set status")) {
                // Change issue status (transition)
                if (issueKey != null) {
                    // First, get available transitions
                    arguments.put("endpoint", "issue/" + issueKey + "/transitions");
                    arguments.put("method", "GET");
                    log.info("Getting transitions for {} (status change requested)", issueKey);
                    // Note: Actual transition requires a follow-up POST call with transition ID
                }
            } else if (lowerQuery.contains("assign") || lowerQuery.contains("assignee")) {
                // Assign issue
                if (issueKey != null) {
                    arguments.put("endpoint", "issue/" + issueKey);
                    arguments.put("method", "PUT");
                    
                    String assignee = extractAssignee(query);
                    if (assignee != null) {
                        arguments.put("data", Map.of("fields", Map.of("assignee", Map.of("name", assignee))));
                        log.info("Assigning {} to {}", issueKey, assignee);
                    }
                }
            } else if (issueKey != null && !arguments.containsKey("endpoint")) {
                // Default: Single issue fetch
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
     * Extract comment text from query
     */
    private String extractCommentText(String query) {
        // Try to extract text after "saying:" or "with text:" or after issue key
        String[] markers = {"saying:", "with comment:", "with text:", "comment:"};
        for (String marker : markers) {
            int idx = query.toLowerCase().indexOf(marker);
            if (idx >= 0) {
                return query.substring(idx + marker.length()).trim();
            }
        }
        // Fallback: use the whole query as comment
        return "Comment added via Agent Framework: " + query;
    }
    
    /**
     * Extract description text from query
     */
    private String extractDescriptionText(String query) {
        String[] markers = {"to:", "with:", "description:"};
        for (String marker : markers) {
            int idx = query.toLowerCase().indexOf(marker);
            if (idx >= 0) {
                return query.substring(idx + marker.length()).trim();
            }
        }
        return "Updated via Agent Framework";
    }
    
    /**
     * Extract assignee from query
     */
    private String extractAssignee(String query) {
        String[] markers = {"to ", "assignee "};
        for (String marker : markers) {
            int idx = query.toLowerCase().indexOf(marker);
            if (idx >= 0) {
                String rest = query.substring(idx + marker.length()).trim();
                // Take the first word as username
                String[] parts = rest.split("\\s+");
                if (parts.length > 0) {
                    return parts[0];
                }
            }
        }
        return null;
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
