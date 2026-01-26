package com.agentframework.service;

import com.agentframework.dto.AgentSpec;
import com.agentframework.dto.CreateAgentRequest;
import com.agentframework.registry.MCPTool;
import com.agentframework.registry.MCPToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Meta-Agent Service that analyzes agent descriptions and generates
 * comprehensive agent specifications using available MCP tools.
 * 
 * Uses LLM when enabled, falls back to keyword-based analysis otherwise.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetaAgentService {

    private final MCPToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final LLMService llmService;

    /**
     * Builds an agent specification from a simple name and description.
     * Uses LLM if enabled, otherwise falls back to keyword-based analysis.
     */
    public AgentSpec buildAgentSpec(CreateAgentRequest request) {
        log.info("Building agent spec for: {}", request.getName());

        // Get all available tools for context
        List<MCPTool> availableTools = toolRegistry.getAllTools();

        // Try LLM-based analysis first if enabled
        if (llmService.isEnabled()) {
            log.info("Using LLM for agent spec generation");
            AgentSpec llmSpec = buildAgentSpecWithLLM(request, availableTools);
            if (llmSpec != null) {
                return llmSpec;
            }
            log.warn("LLM analysis failed, falling back to keyword-based analysis");
        }

        // Fallback to keyword-based analysis
        return buildAgentSpecWithKeywordAnalysis(request, availableTools);
    }

    /**
     * Build agent spec using LLM
     */
    private AgentSpec buildAgentSpecWithLLM(CreateAgentRequest request, List<MCPTool> availableTools) {
        try {
            LLMService.LLMAnalysisResult result = llmService.analyzeAgentDescription(
                    request.getName(),
                    request.getDescription(),
                    availableTools
            );

            if (result == null || result.getSelectedTools() == null || result.getSelectedTools().isEmpty()) {
                return null;
            }

            log.info("LLM selected tools: {} with reasoning: {}", 
                    result.getSelectedTools(), result.getReasoning());

            return AgentSpec.builder()
                    .agentName(request.getName())
                    .goal(result.getGoal() != null ? result.getGoal() : request.getDescription())
                    .allowedTools(result.getSelectedTools())
                    .executionMode(result.getExecutionMode())
                    .permissions(result.getPermissions())
                    .expectedInputs(result.getExpectedInputs())
                    .build();

        } catch (Exception e) {
            log.error("Error in LLM-based spec generation: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Build agent spec using intelligent keyword-based analysis
     */
    private AgentSpec buildAgentSpecWithKeywordAnalysis(CreateAgentRequest request, List<MCPTool> availableTools) {
        String description = request.getDescription().toLowerCase();
        String name = request.getName().toLowerCase();
        String combinedText = name + " " + description;

        // Extract keywords from the description
        List<String> keywords = extractKeywords(combinedText);
        log.debug("Extracted keywords: {}", keywords);
        
        // Find matching tools based on keywords
        List<String> matchedTools = findMatchingTools(keywords, availableTools);
        log.debug("Matched tools: {}", matchedTools);
        
        // Determine execution mode
        String executionMode = determineExecutionMode(description);
        
        // Determine permissions
        List<String> permissions = determinePermissions(description);
        
        // Extract expected inputs
        List<String> expectedInputs = extractExpectedInputs(description, matchedTools, availableTools);
        
        // Generate goal from description
        String goal = generateGoal(request.getDescription());

        return AgentSpec.builder()
                .agentName(request.getName())
                .goal(goal)
                .allowedTools(matchedTools)
                .executionMode(executionMode)
                .permissions(permissions)
                .expectedInputs(expectedInputs)
                .build();
    }

    private List<String> extractKeywords(String text) {
        // Domain-specific keyword mappings for available MCP servers
        // Available MCP Servers: Confluence, Jira, GitHub, Webex
        Map<String, List<String>> domainKeywords = new LinkedHashMap<>();
        
        // Jira keywords
        domainKeywords.put("jira", List.of("jira", "ticket", "issue", "bug", "task", "sprint", 
                "backlog", "story", "epic", "kanban", "scrum", "assignee", "reporter"));
        
        // Confluence keywords (removed "space" to avoid conflict with Webex)
        domainKeywords.put("confluence", List.of("confluence", "wiki", "doc", "documentation", 
                "page", "knowledge", "article", "content"));
        
        // GitHub keywords
        domainKeywords.put("github", List.of("github", "code", "repository", "repo", "commit", 
                "pull request", "pr", "branch", "merge", "diff", "review"));

        // Webex keywords (check for "webex" first for specificity)
        domainKeywords.put("webex", List.of("webex", "webex space", "chat", "room", 
                "conversation", "team", "meeting", "rag", "ask space", "index"));

        List<String> keywords = new ArrayList<>();
        
        for (Map.Entry<String, List<String>> entry : domainKeywords.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (text.contains(keyword)) {
                    keywords.add(entry.getKey());
                    break;
                }
            }
        }

        // Add action-related keywords with HIGH priority for specific actions
        // Order matters - more specific patterns first
        Map<String, List<String>> actionMappings = new LinkedHashMap<>();
        
        // "find space" or "find spaceId" should use list, not search/ask
        if (text.contains("find space") || text.contains("find spaceid") || 
            text.contains("get space") || text.contains("which space")) {
            keywords.add("list");
        }
        
        actionMappings.put("post", List.of("post", "send", "write message", "post message"));
        actionMappings.put("list", List.of("list", "show", "get all", "fetch all", "show me"));
        actionMappings.put("get", List.of("fetch", "retrieve", "read"));
        actionMappings.put("search", List.of("search", "query", "look for"));
        actionMappings.put("create", List.of("create", "new", "add"));
        actionMappings.put("update", List.of("update", "modify", "change", "edit", "assign", "transition", "move to"));
        actionMappings.put("delete", List.of("delete", "remove"));
        actionMappings.put("comment", List.of("comment", "add comment", "post comment"));
        actionMappings.put("ask", List.of("ask question", "inquire about"));  // More specific - avoid false matches
        actionMappings.put("index", List.of("index", "rag"));
        actionMappings.put("api", List.of("issue details", "issue", "rest api", "api call"));  // For REST API calls
        
        for (Map.Entry<String, List<String>> entry : actionMappings.entrySet()) {
            for (String phrase : entry.getValue()) {
                if (text.contains(phrase)) {
                    keywords.add(entry.getKey());
                    break;
                }
            }
        }

        log.info("Extracted keywords from '{}': {}", text, keywords);
        return keywords;
    }

    private List<String> findMatchingTools(List<String> keywords, List<MCPTool> availableTools) {
        Map<String, Integer> toolScores = new HashMap<>();
        
        // Domain keywords indicate which MCP server to prioritize
        Set<String> domainKeywords = Set.of("jira", "confluence", "github", "webex");
        Set<String> actionKeywords = Set.of("post", "list", "get", "search", "create", "update", "delete", "ask", "index", "api", "comment");
        
        Set<String> activeDomains = keywords.stream()
                .filter(domainKeywords::contains)
                .collect(Collectors.toSet());
        
        Set<String> activeActions = keywords.stream()
                .filter(actionKeywords::contains)
                .collect(Collectors.toSet());

        log.info("Active domains: {}, Active actions: {}", activeDomains, activeActions);

        for (MCPTool tool : availableTools) {
            int score = 0;
            String toolName = tool.getName().toLowerCase();
            String toolCategory = tool.getCategory().toLowerCase();

            // CRITICAL: Only consider tools from matching domains
            boolean domainMatch = activeDomains.isEmpty() || activeDomains.contains(toolCategory);
            if (!domainMatch) {
                continue; // Skip tools from other domains entirely
            }

            // Base score for domain match
            if (activeDomains.contains(toolCategory)) {
                score += 5;
            }

            // HIGH priority: Exact action match in tool name or capabilities
            for (String action : activeActions) {
                // Check if action is in tool name (e.g., "post_message" contains "post")
                if (toolName.contains(action)) {
                    score += 20; // High score for action in tool name
                    log.debug("Tool {} matches action '{}' in name", tool.getName(), action);
                }
                // Check capabilities
                for (String capability : tool.getCapabilities()) {
                    if (capability.equalsIgnoreCase(action)) {
                        score += 15; // High score for exact capability match
                    }
                }
            }
            
            // SPECIAL: Prefer REST API tools for issue/page/repo operations
            if (toolName.contains("rest_api") || toolName.contains("call_") || toolName.contains("_api")) {
                if (activeActions.contains("api") || activeActions.contains("get") || 
                    activeActions.contains("create") || activeActions.contains("update")) {
                    score += 25; // Boost REST API tools for CRUD operations
                    log.debug("Tool {} gets REST API boost for CRUD operation", tool.getName());
                }
            }

            if (score > 0) {
                toolScores.put(tool.getName(), score);
                log.debug("Tool {} scored {}", tool.getName(), score);
            }
        }

        // Return only the best matching tool(s) - limit to 1 or 2 for focused execution
        List<String> result = toolScores.entrySet().stream()
                .filter(e -> e.getValue() >= 10) // Higher threshold for precision
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(2) // Only top 2 tools for focused execution
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        
        // Fallback: if no tools matched with high score, lower threshold
        if (result.isEmpty()) {
            result = toolScores.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(2)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }
        
        log.info("Selected tools: {}", result);
        return result;
    }

    private String determineExecutionMode(String description) {
        // Complex tasks that require multiple steps
        List<String> dynamicIndicators = List.of(
                "analyze", "investigate", "identify", "find pattern",
                "root cause", "correlate", "comprehensive", "multiple",
                "anomaly", "anomalies", "patterns"
        );

        for (String indicator : dynamicIndicators) {
            if (description.contains(indicator)) {
                return "dynamic";
            }
        }

        return "static";
    }

    private List<String> determinePermissions(String description) {
        List<String> permissions = new ArrayList<>();

        // Check for write indicators (including "post" and "send" for messaging)
        List<String> writeIndicators = List.of("create", "update", "modify", "delete", "write", "add", "remove", 
                "post", "send", "comment", "assign", "transition", "change status", "move to");
        boolean needsWrite = writeIndicators.stream().anyMatch(description::contains);

        if (needsWrite) {
            permissions.add("write");
        } else {
            permissions.add("read_only");
        }

        // Check for execution indicators
        List<String> executeIndicators = List.of("run", "execute", "deploy", "trigger");
        if (executeIndicators.stream().anyMatch(description::contains)) {
            permissions.add("execute");
        }

        return permissions;
    }

    private List<String> extractExpectedInputs(String description, List<String> matchedTools, 
                                                List<MCPTool> availableTools) {
        Set<String> inputs = new LinkedHashSet<>();

        // Common input patterns based on description
        if (description.contains("time") || description.contains("recent") || 
            description.contains("last") || description.contains("period") ||
            description.contains("anomal")) {
            inputs.add("time_range");
        }

        if (description.contains("service") || description.contains("application")) {
            inputs.add("service");
        }

        // Add required inputs from matched tools only
        for (String toolName : matchedTools) {
            availableTools.stream()
                    .filter(t -> t.getName().equals(toolName))
                    .findFirst()
                    .ifPresent(tool -> {
                        // Add required inputs from matched tools
                        tool.getRequiredInputs().forEach(inputs::add);
                    });
        }

        // Ensure we have at least some basic inputs
        if (inputs.isEmpty()) {
            inputs.add("query");
        }

        return new ArrayList<>(inputs);
    }

    private String generateGoal(String description) {
        // Clean up and transform description into a goal
        String goal = description.trim();
        
        // Remove common prefixes
        String[] prefixesToRemove = {"i want to", "i need to", "please", "help me"};
        String lowerGoal = goal.toLowerCase();
        for (String prefix : prefixesToRemove) {
            if (lowerGoal.startsWith(prefix)) {
                goal = goal.substring(prefix.length()).trim();
                lowerGoal = goal.toLowerCase();
            }
        }

        // Capitalize first letter
        if (!goal.isEmpty()) {
            goal = Character.toUpperCase(goal.charAt(0)) + goal.substring(1);
        }

        // Remove trailing period
        goal = goal.replaceAll("\\.$", "");
        
        return goal;
    }
}
