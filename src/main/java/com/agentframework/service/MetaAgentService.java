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
 * Currently uses keyword-based analysis. LLM integration can be enabled later.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetaAgentService {

    private final MCPToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    @Value("${llm.enabled:false}")
    private boolean llmEnabled;

    /**
     * Builds an agent specification from a simple name and description.
     * Uses intelligent keyword-based analysis to match tools.
     */
    public AgentSpec buildAgentSpec(CreateAgentRequest request) {
        log.info("Building agent spec for: {}", request.getName());

        // Get all available tools for context
        List<MCPTool> availableTools = toolRegistry.getAllTools();

        // Use keyword-based analysis
        return buildAgentSpecWithKeywordAnalysis(request, availableTools);
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
        // Available MCP Servers: Confluence, Jira, GitHub
        Map<String, List<String>> domainKeywords = new LinkedHashMap<>();
        
        // Jira keywords
        domainKeywords.put("jira", List.of("jira", "ticket", "issue", "bug", "task", "sprint", 
                "backlog", "story", "epic", "kanban", "scrum", "assignee", "reporter"));
        
        // Confluence keywords
        domainKeywords.put("confluence", List.of("confluence", "wiki", "doc", "documentation", 
                "page", "knowledge", "article", "space", "content"));
        
        // GitHub keywords
        domainKeywords.put("github", List.of("github", "code", "repository", "repo", "commit", 
                "pull request", "pr", "branch", "merge", "diff", "review"));

        List<String> keywords = new ArrayList<>();
        
        for (Map.Entry<String, List<String>> entry : domainKeywords.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (text.contains(keyword)) {
                    keywords.add(entry.getKey());
                    break;
                }
            }
        }

        // Also add action-related keywords
        List<String> actionKeywords = List.of(
                "analyze", "search", "find", "get", "create", "update", "delete",
                "list", "add", "comment", "transition", "move", "read", "write"
        );
        
        for (String keyword : actionKeywords) {
            if (text.contains(keyword)) {
                keywords.add(keyword);
            }
        }

        return keywords;
    }

    private List<String> findMatchingTools(List<String> keywords, List<MCPTool> availableTools) {
        Map<String, Integer> toolScores = new HashMap<>();
        
        // Domain keywords indicate which MCP server to prioritize
        // Available MCP Servers: Confluence, Jira (sjc12), GitHub (aicodinggithub)
        Set<String> domainKeywords = Set.of("jira", "confluence", "github");
        Set<String> activeDomains = keywords.stream()
                .filter(domainKeywords::contains)
                .collect(Collectors.toSet());

        for (MCPTool tool : availableTools) {
            int score = 0;
            String toolName = tool.getName().toLowerCase();
            String toolCategory = tool.getCategory().toLowerCase();
            String toolText = (tool.getDescription() + " " +
                    String.join(" ", tool.getCapabilities())).toLowerCase();

            // High priority: Tool category matches domain keywords (MCP server)
            if (activeDomains.contains("jira") && toolCategory.equals("jira")) {
                score += 10;
            }
            if (activeDomains.contains("confluence") && toolCategory.equals("confluence")) {
                score += 10;
            }
            if (activeDomains.contains("github") && toolCategory.equals("github")) {
                score += 10;
            }

            // Medium priority: Action keywords match tool capabilities
            for (String keyword : keywords) {
                if (!domainKeywords.contains(keyword)) { // Skip domain keywords for this check
                    for (String capability : tool.getCapabilities()) {
                        if (capability.toLowerCase().contains(keyword.toLowerCase())) {
                            score += 3;
                        }
                    }
                }
            }
            
            // Lower priority: General text matches in description
            for (String keyword : keywords) {
                if (!domainKeywords.contains(keyword) && toolText.contains(keyword.toLowerCase())) {
                    score += 1;
                }
            }

            if (score > 0) {
                toolScores.put(tool.getName(), score);
            }
        }

        // Return tools sorted by score, taking top matches with minimum score threshold
        return toolScores.entrySet().stream()
                .filter(e -> e.getValue() >= 3) // Minimum relevance threshold
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
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

        // Check for write indicators
        List<String> writeIndicators = List.of("create", "update", "modify", "delete", "write", "add", "remove");
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
