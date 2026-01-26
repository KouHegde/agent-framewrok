package com.agentframework.service;

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

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service to fetch data from MCP servers.
 * Used for:
 * 1. Config decision - fetch sample data to understand structure and size
 * 2. RAG pipeline - fetch full data for embedding
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MCPDataFetcherService {

    private final ObjectMapper objectMapper;
    private final AtomicLong requestIdCounter = new AtomicLong(1);

    @Value("${mcp.jira.url:}")
    private String jiraMcpUrl;

    @Value("${mcp.confluence.url:}")
    private String confluenceMcpUrl;

    @Value("${mcp.github.url:}")
    private String githubMcpUrl;

    @Value("${mcp.jira.pat-token:}")
    private String jiraPatToken;

    @Value("${mcp.confluence.pat-token:}")
    private String confluencePatToken;

    @Value("${mcp.github.pat-token:}")
    private String githubPatToken;

    private WebClient jiraClient;
    private WebClient confluenceClient;
    private WebClient githubClient;

    @PostConstruct
    public void init() {
        if (jiraMcpUrl != null && !jiraMcpUrl.isBlank()) {
            jiraClient = createWebClient(jiraMcpUrl, jiraPatToken);
        }
        if (confluenceMcpUrl != null && !confluenceMcpUrl.isBlank()) {
            confluenceClient = createWebClient(confluenceMcpUrl, confluencePatToken);
        }
        if (githubMcpUrl != null && !githubMcpUrl.isBlank()) {
            githubClient = createWebClient(githubMcpUrl, githubPatToken);
        }
    }

    private WebClient createWebClient(String baseUrl, String token) {
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json, text/event-stream");

        if (token != null && !token.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        return builder.build();
    }

    /**
     * Fetch sample data from an MCP server for config decision.
     * Returns limited data to understand structure without heavy load.
     */
    public Object fetchSampleData(String category, String toolName) {
        log.debug("Fetching sample data from {} using {}", category, toolName);

        try {
            if (!isClientConfigured(category)) {
                log.warn("MCP client not configured for: {}", category);
                return Map.of("error", "mcp_client_not_configured", "category", category);
            }
            Map<String, Object> arguments = buildSampleArguments(category);
            return callMcp(category, extractToolName(toolName), arguments);
        } catch (Exception e) {
            log.warn("Failed to fetch sample data from {}: {}", category, e.getMessage());
            return Map.of("error", "mcp_sample_fetch_failed", "category", category, "message", e.getMessage());
        }
    }

    /**
     * Fetch and summarize MCP data for config decision.
     * Returns a small, structured snapshot to reduce memory usage.
     */
    public Map<String, Object> fetchDecisionData(String category, String toolName) {
        Object data = fetchSampleData(category, toolName);
        if (data == null) {
            return Map.of("error", "mcp_sample_unavailable", "category", category);
        }
        return summarizeForDecision(data);
    }

    /**
     * Fetch full data from an MCP server for RAG pipeline.
     */
    public Object fetchFullData(String category, String toolName, Map<String, Object> arguments) {
        log.info("Fetching full data from {} using {}", category, toolName);

        try {
            return callMcp(category, extractToolName(toolName), arguments);
        } catch (Exception e) {
            log.error("Failed to fetch data from {}: {}", category, e.getMessage());
            throw new RuntimeException("MCP data fetch failed", e);
        }
    }

    /**
     * Fetch data from multiple MCP servers in parallel.
     */
    public Map<String, Object> fetchFromMultipleSources(Map<String, Map<String, Object>> requests) {
        Map<String, Object> results = new HashMap<>();

        // TODO: Implement parallel fetching with CompletableFuture
        for (Map.Entry<String, Map<String, Object>> entry : requests.entrySet()) {
            String category = entry.getKey();
            Map<String, Object> args = entry.getValue();
            String toolName = (String) args.getOrDefault("toolName", getDefaultTool(category));

            try {
                Object data = callMcp(category, toolName, args);
                results.put(category, data);
            } catch (Exception e) {
                log.warn("Failed to fetch from {}: {}", category, e.getMessage());
                results.put(category, Map.of("error", e.getMessage()));
            }
        }

        return results;
    }

    private Object callMcp(String category, String toolName, Map<String, Object> arguments) throws Exception {
        WebClient client = getClient(category);
        if (client == null) {
            throw new IllegalStateException("MCP client not configured for: " + category);
        }

        Map<String, Object> jsonRpcRequest = new LinkedHashMap<>();
        jsonRpcRequest.put("jsonrpc", "2.0");
        jsonRpcRequest.put("id", requestIdCounter.getAndIncrement());
        jsonRpcRequest.put("method", "tools/call");
        jsonRpcRequest.put("params", Map.of(
                "name", toolName,
                "arguments", arguments
        ));

        log.debug("MCP Request to {}: {}", category, toolName);

        String response = client.post()
                .bodyValue(jsonRpcRequest)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (response == null || response.isBlank()) {
            return Map.of("empty", true);
        }

        // Parse SSE format if present
        String jsonData = parseSseResponse(response);
        JsonNode responseNode = objectMapper.readTree(jsonData);

        if (responseNode.has("error")) {
            throw new RuntimeException("MCP error: " + responseNode.get("error").path("message").asText());
        }

        return responseNode.has("result") ? responseNode.get("result") : responseNode;
    }

    private Map<String, Object> summarizeForDecision(Object data) {
        Map<String, Object> summary = new HashMap<>();
        if (data == null) {
            return summary;
        }

        try {
            JsonNode node = (data instanceof JsonNode) ? (JsonNode) data : objectMapper.valueToTree(data);
            JsonNode slim = slimNode(node, 0, 2, 10, 200);
            long sizeBytes = objectMapper.writeValueAsString(slim).getBytes().length;
            summary.put("sample", slim);
            summary.put("sizeBytes", sizeBytes);
            summary.put("type", node.getNodeType().name());
            return summary;
        } catch (Exception e) {
            log.warn("Failed to summarize MCP data: {}", e.getMessage());
            return summary;
        }
    }

    private JsonNode slimNode(JsonNode node,
                              int depth,
                              int maxDepth,
                              int maxItems,
                              int maxStringLen) {
        if (node == null || depth > maxDepth) {
            return objectMapper.nullNode();
        }

        if (node.isObject()) {
            var obj = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            int count = 0;
            while (fields.hasNext() && count < maxItems) {
                Map.Entry<String, JsonNode> entry = fields.next();
                obj.set(entry.getKey(), slimNode(entry.getValue(), depth + 1, maxDepth, maxItems, maxStringLen));
                count++;
            }
            return obj;
        }

        if (node.isArray()) {
            var arr = objectMapper.createArrayNode();
            int count = 0;
            for (JsonNode item : node) {
                if (count >= maxItems) {
                    break;
                }
                arr.add(slimNode(item, depth + 1, maxDepth, maxItems, maxStringLen));
                count++;
            }
            return arr;
        }

        if (node.isTextual()) {
            String text = node.asText();
            if (text.length() > maxStringLen) {
                return objectMapper.getNodeFactory().textNode(text.substring(0, maxStringLen));
            }
            return node;
        }

        return node;
    }

    private String parseSseResponse(String response) {
        String[] lines = response.split("\n");
        for (String line : lines) {
            if (line.startsWith("data: ")) {
                return line.substring(6).trim();
            }
        }
        return response.trim().startsWith("{") ? response.trim() : "{}";
    }

    private WebClient getClient(String category) {
        return switch (category.toLowerCase()) {
            case "jira" -> jiraClient;
            case "confluence" -> confluenceClient;
            case "github" -> githubClient;
            default -> null;
        };
    }

    private boolean isClientConfigured(String category) {
        return getClient(category) != null;
    }

    private Map<String, Object> buildSampleArguments(String category) {
        return switch (category.toLowerCase()) {
            case "jira" -> Map.of(
                    "endpoint", "search",
                    "method", "GET",
                    "params", Map.of(
                            "jql", "updated >= -7d ORDER BY updated DESC",
                            "maxResults", 10  // Limit for sample
                    )
            );
            case "confluence" -> Map.of(
                    "cql_query", "type = page ORDER BY lastmodified DESC",
                    "limit", 10  // Limit for sample
            );
            case "github" -> Map.of(
                    "resource", "repositories",
                    "parameters", Map.of(
                            "sort", "updated",
                            "per_page", 10  // Limit for sample
                    )
            );
            default -> Map.of();
        };
    }

    private String getDefaultTool(String category) {
        return switch (category.toLowerCase()) {
            case "jira" -> "call_jira_rest_api";
            case "confluence" -> "search_confluence_pages";
            case "github" -> "call_github_restapi_for_search";
            default -> "";
        };
    }

    private String extractToolName(String fullName) {
        String[] knownTools = {
                "call_jira_rest_api", "add_labels", "get_field_info",
                "search_confluence_pages", "get_confluence_page_by_id",
                "call_confluence_rest_api",
                "call_github_graphql_for_query", "get_pull_request_diff",
                "call_github_restapi_for_search"
        };

        for (String tool : knownTools) {
            if (fullName.endsWith(tool)) {
                return tool;
            }
        }

        // Fallback
        if (fullName.contains("jira")) return "call_jira_rest_api";
        if (fullName.contains("confluence")) return "search_confluence_pages";
        if (fullName.contains("github")) return "call_github_restapi_for_search";

        return fullName;
    }
}
