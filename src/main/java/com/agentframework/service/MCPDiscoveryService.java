package com.agentframework.service;

import com.agentframework.registry.MCPTool;
import com.agentframework.registry.MCPToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service that dynamically discovers available tools from MCP servers.
 * 
 * At startup, this service calls each configured MCP server's tools/list endpoint
 * and registers the discovered tools in the MCPToolRegistry.
 * 
 * MCP Protocol for tool discovery:
 * Request:
 * {
 *   "jsonrpc": "2.0",
 *   "id": 1,
 *   "method": "tools/list",
 *   "params": {}
 * }
 * 
 * Response:
 * {
 *   "jsonrpc": "2.0",
 *   "id": 1,
 *   "result": {
 *     "tools": [
 *       {
 *         "name": "call_jira_rest_api",
 *         "description": "Call Jira REST API",
 *         "inputSchema": { ... }
 *       }
 *     ]
 *   }
 * }
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MCPDiscoveryService {

    private final MCPToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    
    private final AtomicLong requestIdCounter = new AtomicLong(1);

    // MCP Server configurations
    @Value("${mcp.jira.url:}")
    private String jiraMcpUrl;

    @Value("${mcp.jira.pat-token:}")
    private String jiraPatToken;

    @Value("${mcp.confluence.url:}")
    private String confluenceMcpUrl;

    @Value("${mcp.confluence.pat-token:}")
    private String confluencePatToken;

    @Value("${mcp.github.url:}")
    private String githubMcpUrl;

    @Value("${mcp.github.pat-token:}")
    private String githubPatToken;

    @Value("${mcp.webex.url:}")
    private String webexMcpUrl;

    @Value("${mcp.webex.token:}")
    private String webexToken;

    @Value("${mcp.discovery.enabled:true}")
    private boolean discoveryEnabled;

    @PostConstruct
    public void discoverTools() {
        if (!discoveryEnabled) {
            log.info("MCP tool discovery is disabled. Using static tool registry.");
            return;
        }

        log.info("Starting MCP tool discovery...");

        // Discover tools from each configured MCP server
        discoverFromServer("jira", jiraMcpUrl, jiraPatToken, false);
        discoverFromServer("confluence", confluenceMcpUrl, confluencePatToken, false);
        discoverFromServer("github", githubMcpUrl, githubPatToken, false);
        discoverFromServer("webex", webexMcpUrl, webexToken, true); // Webex uses different API

        log.info("MCP tool discovery completed. Total tools registered: {}", 
                toolRegistry.getAllTools().size());
    }

    /**
     * Discover tools from a single MCP server
     */
    private void discoverFromServer(String category, String serverUrl, String token, boolean isRestApi) {
        if (serverUrl == null || serverUrl.isBlank()) {
            log.debug("Skipping {} MCP discovery - URL not configured", category);
            return;
        }

        try {
            log.info("Discovering tools from {} MCP server: {}", category, serverUrl);

            if (isRestApi) {
                // Webex-style: GET /mcp/tools
                discoverFromRestApi(category, serverUrl, token);
            } else {
                // JSON-RPC style: POST with tools/list method
                discoverFromJsonRpc(category, serverUrl, token);
            }

        } catch (Exception e) {
            log.warn("Failed to discover tools from {} MCP server: {}", category, e.getMessage());
            log.debug("Discovery error details", e);
        }
    }

    /**
     * Discover tools using JSON-RPC protocol (Jira, Confluence, GitHub)
     */
    private void discoverFromJsonRpc(String category, String serverUrl, String token) {
        WebClient client = createWebClient(serverUrl, token);

        // Build JSON-RPC request for tools/list
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", requestIdCounter.getAndIncrement());
        request.put("method", "tools/list");
        request.put("params", Map.of());

        try {
            String response = client.post()
                    .uri("")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null) {
                parseAndRegisterTools(category, response);
            }

        } catch (Exception e) {
            log.warn("JSON-RPC discovery failed for {}: {}", category, e.getMessage());
        }
    }

    /**
     * Discover tools using REST API (Webex)
     */
    private void discoverFromRestApi(String category, String serverUrl, String token) {
        WebClient client = WebClient.builder()
                .baseUrl(serverUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-Webex-Token", token)
                .build();

        try {
            String response = client.get()
                    .uri("/mcp/tools")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null) {
                parseAndRegisterRestTools(category, response);
            }

        } catch (Exception e) {
            log.warn("REST API discovery failed for {}: {}", category, e.getMessage());
        }
    }

    /**
     * Parse JSON-RPC response and register tools
     */
    private void parseAndRegisterTools(String category, String response) {
        try {
            // Handle SSE format if present
            String jsonData = parseSseResponse(response);
            JsonNode root = objectMapper.readTree(jsonData);

            // Check for error
            if (root.has("error")) {
                log.warn("MCP server returned error: {}", root.get("error"));
                return;
            }

            // Get tools array from result
            JsonNode result = root.path("result");
            JsonNode toolsArray = result.path("tools");

            if (!toolsArray.isArray()) {
                log.warn("No tools array found in response for {}", category);
                return;
            }

            int count = 0;
            for (JsonNode toolNode : toolsArray) {
                MCPTool tool = parseToolNode(category, toolNode);
                if (tool != null) {
                    toolRegistry.registerTool(tool);
                    count++;
                    log.debug("Registered tool: {}", tool.getName());
                }
            }

            log.info("Discovered {} tools from {} MCP server", count, category);

        } catch (Exception e) {
            log.error("Failed to parse tools response for {}: {}", category, e.getMessage());
        }
    }

    /**
     * Parse REST API response and register tools (Webex format)
     */
    private void parseAndRegisterRestTools(String category, String response) {
        try {
            JsonNode root = objectMapper.readTree(response);

            // Webex might return tools directly as an array or in a "tools" field
            JsonNode toolsArray = root.isArray() ? root : root.path("tools");

            if (!toolsArray.isArray()) {
                log.warn("No tools array found in REST response for {}", category);
                return;
            }

            int count = 0;
            for (JsonNode toolNode : toolsArray) {
                MCPTool tool = parseToolNode(category, toolNode);
                if (tool != null) {
                    toolRegistry.registerTool(tool);
                    count++;
                    log.debug("Registered tool: {}", tool.getName());
                }
            }

            log.info("Discovered {} tools from {} MCP server (REST)", count, category);

        } catch (Exception e) {
            log.error("Failed to parse REST tools response for {}: {}", category, e.getMessage());
        }
    }

    /**
     * Parse a single tool node and create MCPTool
     */
    private MCPTool parseToolNode(String category, JsonNode toolNode) {
        String name = toolNode.path("name").asText(null);
        String description = toolNode.path("description").asText("");

        if (name == null || name.isBlank()) {
            return null;
        }

        // Prefix tool name with mcp_{category}_ for consistency
        String fullName = "mcp_" + category + "_" + name;

        // Extract required inputs from inputSchema
        List<String> requiredInputs = extractRequiredInputs(toolNode.path("inputSchema"));

        // Extract capabilities from description keywords
        List<String> capabilities = extractCapabilities(name, description);

        return MCPTool.builder()
                .name(fullName)
                .description(description)
                .category(category)
                .capabilities(capabilities)
                .requiredInputs(requiredInputs)
                .build();
    }

    /**
     * Extract required inputs from JSON Schema
     */
    private List<String> extractRequiredInputs(JsonNode inputSchema) {
        List<String> required = new ArrayList<>();
        
        if (inputSchema.has("required") && inputSchema.get("required").isArray()) {
            for (JsonNode req : inputSchema.get("required")) {
                required.add(req.asText());
            }
        }
        
        return required;
    }

    /**
     * Extract capabilities from tool name and description
     */
    private List<String> extractCapabilities(String name, String description) {
        Set<String> capabilities = new HashSet<>();
        String combined = (name + " " + description).toLowerCase();

        // Common action keywords
        List<String> actionKeywords = List.of(
                "search", "get", "list", "create", "update", "delete",
                "add", "remove", "post", "send", "read", "write",
                "query", "fetch", "find"
        );

        for (String keyword : actionKeywords) {
            if (combined.contains(keyword)) {
                capabilities.add(keyword);
            }
        }

        // Add name parts as capabilities
        String[] nameParts = name.toLowerCase().split("[_\\-]");
        for (String part : nameParts) {
            if (part.length() > 2) {
                capabilities.add(part);
            }
        }

        return new ArrayList<>(capabilities);
    }

    /**
     * Parse SSE format response
     */
    private String parseSseResponse(String response) {
        if (response == null || response.isBlank()) {
            return "{}";
        }

        String[] lines = response.split("\n");
        for (String line : lines) {
            if (line.startsWith("data: ")) {
                return line.substring(6).trim();
            }
        }

        if (response.trim().startsWith("{")) {
            return response.trim();
        }

        return "{}";
    }

    /**
     * Create WebClient for MCP communication
     */
    private WebClient createWebClient(String baseUrl, String token) {
        HttpClient httpClient = HttpClient.create()
                .followRedirect(true)
                .responseTimeout(Duration.ofSeconds(10));

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json, text/event-stream");

        if (token != null && !token.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        return builder.build();
    }

    /**
     * Manually trigger tool discovery (for refresh)
     */
    public void refresh() {
        log.info("Refreshing MCP tool registry...");
        toolRegistry.clear();
        discoverTools();
    }
}

