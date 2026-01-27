package com.agentframework.registry;

import com.agentframework.data.entity.McpToolEntity;
import com.agentframework.data.repository.McpToolRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry of available MCP tools.
 * 
 * Tools are registered based on MCP server documentation.
 * Each tool maps to an MCP server endpoint.
 */
@Component
public class MCPToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(MCPToolRegistry.class);

    private final Map<String, MCPTool> tools = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private McpToolRepository mcpToolRepository;

    @PostConstruct
    public void init() {
        List<MCPTool> configTools = buildConfigTools();

        if (mcpToolRepository == null) {
            log.info("MCP tool database not configured. Using config registry.");
            registerTools(configTools);
            return;
        }

        // Cleanup old Webex tools with mcp_webex_ prefix (migrated to simple names)
        cleanupOldWebexTools();

        try {
            Map<String, MCPTool> mergedTools = new LinkedHashMap<>();
            for (McpToolEntity entity : mcpToolRepository.findAll()) {
                MCPTool tool = toRegistryTool(entity);
                if (tool != null) {
                    mergedTools.put(tool.getName(), tool);
                }
            }

            List<McpToolEntity> toPersist = new ArrayList<>();
            for (MCPTool tool : configTools) {
                if (!mergedTools.containsKey(tool.getName())) {
                    mergedTools.put(tool.getName(), tool);
                    toPersist.add(toEntity(tool));
                }
            }

            registerTools(new ArrayList<>(mergedTools.values()));

            if (!toPersist.isEmpty()) {
                try {
                    mcpToolRepository.saveAll(toPersist);
                    log.info("Seeded {} MCP tools into database.", toPersist.size());
                } catch (Exception e) {
                    log.warn("Failed to seed MCP tools into database: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load MCP tools from database. Using config registry. Error: {}", e.getMessage());
            registerTools(configTools);
        }
    }

    /**
     * Remove old Webex tools with mcp_webex_ prefix from database.
     * Webex tools now use simple names: list_spaces, post_message, etc.
     */
    private void cleanupOldWebexTools() {
        try {
            List<McpToolEntity> oldTools = mcpToolRepository.findAll().stream()
                    .filter(t -> t.getName() != null && t.getName().startsWith("mcp_webex_"))
                    .toList();
            
            if (!oldTools.isEmpty()) {
                mcpToolRepository.deleteAll(oldTools);
                log.info("Cleaned up {} old Webex tools with mcp_webex_ prefix", oldTools.size());
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup old Webex tools: {}", e.getMessage());
        }
    }

    public void registerTool(MCPTool tool) {
        tools.put(tool.getName(), tool);
    }

    private void registerTools(List<MCPTool> toolsToRegister) {
        toolsToRegister.forEach(this::registerTool);
    }

    private MCPTool toRegistryTool(McpToolEntity entity) {
        if (entity == null || entity.getName() == null || entity.getName().isBlank()) {
            return null;
        }
        return MCPTool.builder()
                .name(entity.getName())
                .description(entity.getDescription())
                .category(entity.getCategory())
                .capabilities(entity.getCapabilities())
                .requiredInputs(entity.getRequiredInputs())
                .build();
    }

    private McpToolEntity toEntity(MCPTool tool) {
        return new McpToolEntity(
                tool.getName(),
                tool.getDescription(),
                tool.getCategory(),
                tool.getCapabilities(),
                tool.getRequiredInputs()
        );
    }

    public Optional<MCPTool> getTool(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public void ensureToolsPersisted(List<String> toolNames) {
        if (mcpToolRepository == null || toolNames == null || toolNames.isEmpty()) {
            return;
        }

        List<String> normalizedNames = toolNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();

        if (normalizedNames.isEmpty()) {
            return;
        }

        try {
            Set<String> existing = new HashSet<>(
                    mcpToolRepository.findByNameIn(normalizedNames).stream()
                            .map(McpToolEntity::getName)
                            .toList()
            );

            List<McpToolEntity> toPersist = new ArrayList<>();
            for (String name : normalizedNames) {
                if (!existing.contains(name)) {
                    MCPTool tool = tools.get(name);
                    if (tool != null) {
                        toPersist.add(toEntity(tool));
                    } else {
                        log.debug("Skipping MCP tool persist; not in registry: {}", name);
                    }
                }
            }

            if (!toPersist.isEmpty()) {
                mcpToolRepository.saveAll(toPersist);
                log.info("Persisted {} MCP tools from create flow.", toPersist.size());
            }
        } catch (Exception e) {
            log.warn("Failed to persist MCP tools from create flow: {}", e.getMessage());
        }
    }

    public List<MCPTool> getAllTools() {
        return new ArrayList<>(tools.values());
    }

    public List<MCPTool> getToolsByCategory(String category) {
        return tools.values().stream()
                .filter(tool -> tool.getCategory().equalsIgnoreCase(category))
                .collect(Collectors.toList());
    }

    public Map<String, List<MCPTool>> getToolsByCategories() {
        return tools.values().stream()
                .collect(Collectors.groupingBy(MCPTool::getCategory));
    }

    /**
     * Find tools that match the given keywords
     */
    public List<MCPTool> findToolsByKeywords(List<String> keywords) {
        return tools.values().stream()
                .filter(tool -> {
                    for (String keyword : keywords) {
                        String lowerKeyword = keyword.toLowerCase();
                        if (tool.getCapabilities().stream()
                                .anyMatch(cap -> cap.toLowerCase().contains(lowerKeyword))) {
                            return true;
                        }
                        if (tool.getDescription().toLowerCase().contains(lowerKeyword)) {
                            return true;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    /**
     * Clear all registered tools (used before dynamic discovery refresh)
     */
    public void clear() {
        tools.clear();
    }

    /**
     * Check if any tools are registered
     */
    public boolean isEmpty() {
        return tools.isEmpty();
    }

    private List<MCPTool> buildConfigTools() {
        List<MCPTool> configTools = new ArrayList<>();

        // ========================================
        // JIRA MCP Tools (FastMCP 2.0 HTTP Transport)
        // Documentation: mcp-jira README
        // ========================================

        // Universal API Access - Direct access to any Jira REST API endpoint
        configTools.add(MCPTool.builder()
                .name("mcp_jira-sjc12_call_jira_rest_api")
                .description("Direct access to any Jira REST API endpoint. Supports GET, POST, PUT, DELETE operations.")
                .category("jira")
                .capabilities(List.of("search", "get", "create", "update", "delete", "jira", "issue", "api"))
                .requiredInputs(List.of("endpoint", "method"))
                .build());

        // Add Labels - Add labels without overriding existing ones
        configTools.add(MCPTool.builder()
                .name("mcp_jira-sjc12_add_labels")
                .description("Add labels to a Jira issue without overriding existing labels")
                .category("jira")
                .capabilities(List.of("add", "labels", "jira", "issue", "update"))
                .requiredInputs(List.of("issue_key", "labels"))
                .build());

        // Field Discovery - Intelligent field lookup with fuzzy search
        configTools.add(MCPTool.builder()
                .name("mcp_jira-sjc12_get_field_info")
                .description("Intelligent field lookup with fuzzy search. Find field IDs by name or search by type.")
                .category("jira")
                .capabilities(List.of("get", "field", "info", "metadata", "jira", "search"))
                .requiredInputs(List.of()) // All params optional
                .build());

        // ========================================
        // CONFLUENCE MCP Tools
        // ========================================

        configTools.add(MCPTool.builder()
                .name("mcp_confluence_search_confluence_pages")
                .description("Search Confluence pages using CQL (Confluence Query Language)")
                .category("confluence")
                .capabilities(List.of("search", "documentation", "wiki", "knowledge", "confluence", "pages"))
                .requiredInputs(List.of("cql_query"))
                .build());

        configTools.add(MCPTool.builder()
                .name("mcp_confluence_get_confluence_page_by_id")
                .description("Get content of a Confluence page by its ID")
                .category("confluence")
                .capabilities(List.of("read", "get", "documentation", "wiki", "content", "confluence", "page"))
                .requiredInputs(List.of("page_id"))
                .build());

        configTools.add(MCPTool.builder()
                .name("mcp_confluence_get_confluence_page_by_title")
                .description("Get content of a Confluence page by space key and title")
                .category("confluence")
                .capabilities(List.of("read", "get", "documentation", "wiki", "content", "confluence", "page"))
                .requiredInputs(List.of("space_key", "title"))
                .build());

        configTools.add(MCPTool.builder()
                .name("mcp_confluence_get_confluence_page_by_url")
                .description("Get content of a Confluence page by URL")
                .category("confluence")
                .capabilities(List.of("read", "get", "documentation", "wiki", "content", "confluence", "page"))
                .requiredInputs(List.of("page_url"))
                .build());

        configTools.add(MCPTool.builder()
                .name("mcp_confluence_call_confluence_rest_api")
                .description("Call any Confluence REST API endpoint")
                .category("confluence")
                .capabilities(List.of("call", "rest", "api", "confluence", "read", "write"))
                .requiredInputs(List.of("endpoint", "method"))
                .build());

        // ========================================
        // GITHUB MCP Tools
        // ========================================

        configTools.add(MCPTool.builder()
                .name("mcp_aicodinggithub_call_github_graphql_for_query")
                .description("Call GitHub GraphQL API for read operations (queries)")
                .category("github")
                .capabilities(List.of("query", "graphql", "github", "read", "code", "repo", "pull_request", "issues"))
                .requiredInputs(List.of("graphql"))
                .build());

        configTools.add(MCPTool.builder()
                .name("mcp_aicodinggithub_get_pull_request_diff")
                .description("Get the diff of a specific pull request")
                .category("github")
                .capabilities(List.of("get", "diff", "pull_request", "pr", "github", "code", "review"))
                .requiredInputs(List.of("owner", "repo", "pull_number"))
                .build());

        configTools.add(MCPTool.builder()
                .name("mcp_aicodinggithub_call_github_restapi_for_search")
                .description("Call GitHub REST API for search operations (code, users)")
                .category("github")
                .capabilities(List.of("search", "rest", "api", "github", "code", "users"))
                .requiredInputs(List.of("resource", "parameters"))
                .build());

        configTools.add(MCPTool.builder()
                .name("mcp_aicodinggithub_call_github_graphql_for_mutation")
                .description("Call GitHub GraphQL API for write operations (mutations)")
                .category("github")
                .capabilities(List.of("mutation", "graphql", "github", "write", "code", "repo", "create", "update"))
                .requiredInputs(List.of("graphql"))
                .build());

        // ========================================
        // WEBEX MCP Tools (REST API pattern)
        // Endpoint: /mcp/tools/{tool_name}
        // Auth: X-Webex-Token header
        // ========================================

        // User & People (names match Webex MCP server exactly)
        configTools.add(MCPTool.builder()
                .name("who_am_i")
                .description("Get the current authenticated Webex user's information")
                .category("webex")
                .capabilities(List.of("webex", "user", "profile", "me", "identity"))
                .requiredInputs(List.of())
                .build());

        configTools.add(MCPTool.builder()
                .name("get_person")
                .description("Get information about a Webex user by their person ID")
                .category("webex")
                .capabilities(List.of("webex", "user", "person", "profile", "get"))
                .requiredInputs(List.of("personId"))
                .build());

        // Spaces
        configTools.add(MCPTool.builder()
                .name("list_spaces")
                .description("List Webex spaces (rooms) the user is a member of")
                .category("webex")
                .capabilities(List.of("webex", "space", "room", "list", "group", "chat"))
                .requiredInputs(List.of())
                .build());

        configTools.add(MCPTool.builder()
                .name("get_space")
                .description("Get details of a specific Webex space")
                .category("webex")
                .capabilities(List.of("webex", "space", "room", "get", "details"))
                .requiredInputs(List.of("spaceId"))
                .build());

        configTools.add(MCPTool.builder()
                .name("list_memberships")
                .description("List members of a Webex space")
                .category("webex")
                .capabilities(List.of("webex", "space", "members", "membership", "list"))
                .requiredInputs(List.of("spaceId"))
                .build());

        configTools.add(MCPTool.builder()
                .name("search_spaces_by_name")
                .description("Search for spaces (rooms) by name or partial name match")
                .category("webex")
                .capabilities(List.of("webex", "search", "space", "find", "name", "lookup"))
                .requiredInputs(List.of("searchTerm"))
                .build());

        // Messages
        configTools.add(MCPTool.builder()
                .name("list_messages")
                .description("List messages in a Webex space with pagination")
                .category("webex")
                .capabilities(List.of("webex", "message", "chat", "list", "conversation"))
                .requiredInputs(List.of("spaceId"))
                .build());

        configTools.add(MCPTool.builder()
                .name("get_message")
                .description("Get a specific message by ID")
                .category("webex")
                .capabilities(List.of("webex", "message", "get", "read"))
                .requiredInputs(List.of("messageId"))
                .build());

        configTools.add(MCPTool.builder()
                .name("post_message")
                .description("Post a message to a Webex space with optional citations")
                .category("webex")
                .capabilities(List.of("webex", "message", "post", "send", "write", "chat"))
                .requiredInputs(List.of("spaceId", "markdown"))
                .build());

        configTools.add(MCPTool.builder()
                .name("get_context_around_message")
                .description("Get messages before and after a specific message for context")
                .category("webex")
                .capabilities(List.of("webex", "message", "context", "thread", "surrounding"))
                .requiredInputs(List.of("spaceId", "messageId"))
                .build());

        // RAG & Search
        configTools.add(MCPTool.builder()
                .name("index_space_messages")
                .description("Index messages from a space into local storage for retrieval")
                .category("webex")
                .capabilities(List.of("webex", "index", "rag", "search", "space", "messages"))
                .requiredInputs(List.of("spaceId"))
                .build());

        configTools.add(MCPTool.builder()
                .name("retrieve_relevant")
                .description("Retrieve relevant past messages for a question using RAG-style retrieval")
                .category("webex")
                .capabilities(List.of("webex", "rag", "search", "retrieve", "relevant", "question"))
                .requiredInputs(List.of("spaceId", "question"))
                .build());

        configTools.add(MCPTool.builder()
                .name("ask_space")
                .description("Ask a question and get a synthesized answer based on conversation history in the space")
                .category("webex")
                .capabilities(List.of("webex", "ask", "question", "answer", "ai", "rag", "search"))
                .requiredInputs(List.of("spaceId", "question"))
                .build());

        return configTools;
    }
}
