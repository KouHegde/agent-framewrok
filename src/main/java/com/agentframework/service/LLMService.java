package com.agentframework.service;

import com.agentframework.registry.MCPTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

/**
 * LLM Service for intelligent agent specification generation.
 * 
 * Supports multiple LLM providers:
 * - Cisco LLM Proxy (internal, OpenAI-compatible)
 * - OpenAI (GPT-4, GPT-3.5)
 * - Anthropic (Claude)
 * - Google (Gemini)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMService {

    private final ObjectMapper objectMapper;

    @Value("${llm.provider:none}")
    private String llmProvider;

    @Value("${llm.enabled:false}")
    private boolean llmEnabled;

    @Value("${llm.api.url:}")
    private String llmApiUrl;

    @Value("${llm.api.key:}")
    private String llmApiKey;

    @Value("${llm.model:}")
    private String llmModel;

    private WebClient llmClient;

    @PostConstruct
    public void init() {
        if (!llmEnabled || llmApiKey == null || llmApiKey.isBlank()) {
            log.info("LLM is disabled or not configured. Using keyword-based analysis.");
            return;
        }

        // Set default URLs and models based on provider
        if (llmApiUrl == null || llmApiUrl.isBlank()) {
            switch (llmProvider.toLowerCase()) {
                case "cisco":
                    // Cisco internal LLM proxy (OpenAI-compatible)
                    // Note: Uses /openai/v1/ prefix, not just /v1/
                    llmApiUrl = "https://llm-proxy.us-east-2.int.infra.intelligence.webex.com/openai/v1/chat/completions";
                    if (llmModel == null || llmModel.isBlank()) llmModel = "gpt-4o-mini";
                    break;
                case "openai":
                    llmApiUrl = "https://api.openai.com/v1/chat/completions";
                    if (llmModel == null || llmModel.isBlank()) llmModel = "gpt-4";
                    break;
                case "anthropic":
                    llmApiUrl = "https://api.anthropic.com/v1/messages";
                    if (llmModel == null || llmModel.isBlank()) llmModel = "claude-3-sonnet-20240229";
                    break;
                case "gemini":
                    llmApiUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + 
                               (llmModel != null && !llmModel.isBlank() ? llmModel : "gemini-pro") + 
                               ":generateContent";
                    break;
                default:
                    log.warn("Unknown LLM provider: {}. Supported: cisco, openai, anthropic, gemini", llmProvider);
                    return;
            }
        }

        llmClient = WebClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        log.info("LLM Service initialized: provider={}, model={}", llmProvider, llmModel);
    }

    public boolean isEnabled() {
        return llmEnabled && llmClient != null && llmApiKey != null && !llmApiKey.isBlank();
    }

    /**
     * Analyze agent description and select appropriate tools using LLM
     */
    public LLMAnalysisResult analyzeAgentDescription(String name, String description, List<MCPTool> availableTools) {
        if (!isEnabled()) {
            return null;
        }

        try {
            String prompt = buildAnalysisPrompt(name, description, availableTools);
            String response = callLLM(prompt);
            return parseAnalysisResponse(response);
        } catch (Exception e) {
            log.error("LLM analysis failed: {}", e.getMessage());
            return null;
        }
    }

    private String buildAnalysisPrompt(String name, String description, List<MCPTool> availableTools) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an AI agent specification generator. Analyze the following agent request and select the most appropriate tools.\n\n");
        prompt.append("Agent Name: ").append(name).append("\n");
        prompt.append("Agent Description: ").append(description).append("\n\n");
        prompt.append("Available MCP Tools:\n");

        for (MCPTool tool : availableTools) {
            prompt.append("- ").append(tool.getName())
                  .append(": ").append(tool.getDescription())
                  .append(" (category: ").append(tool.getCategory())
                  .append(", capabilities: ").append(String.join(", ", tool.getCapabilities()))
                  .append(")\n");
        }

        prompt.append("\nRespond with a JSON object containing:\n");
        prompt.append("{\n");
        prompt.append("  \"selectedTools\": [\"tool_name1\", \"tool_name2\"],  // Array of tool names to use\n");
        prompt.append("  \"goal\": \"Clear goal statement\",  // Reformulated goal\n");
        prompt.append("  \"executionMode\": \"static\" or \"dynamic\",  // static for simple, dynamic for multi-step\n");
        prompt.append("  \"permissions\": [\"read_only\"] or [\"write\"] or [\"read_only\", \"execute\"],  // Required permissions\n");
        prompt.append("  \"expectedInputs\": [\"input1\", \"input2\"],  // User inputs needed at runtime\n");
        prompt.append("  \"reasoning\": \"Brief explanation of tool selection\"\n");
        prompt.append("}\n\n");
        prompt.append("Select only the most relevant tools (1-3 max). Respond with valid JSON only.");

        return prompt.toString();
    }

    private String callLLM(String prompt) throws Exception {
        Map<String, Object> request;
        String provider = llmProvider.toLowerCase();

        switch (provider) {
            case "cisco":
            case "openai":
                // Cisco LLM Proxy uses OpenAI-compatible API
                request = buildOpenAIRequest(prompt);
                break;
            case "anthropic":
                request = buildAnthropicRequest(prompt);
                break;
            case "gemini":
                request = buildGeminiRequest(prompt);
                break;
            default:
                throw new RuntimeException("Unsupported LLM provider: " + llmProvider);
        }

        log.debug("Calling LLM API: {} (provider: {})", llmApiUrl, provider);

        String response = llmClient.post()
                .uri(llmApiUrl + (provider.equals("gemini") ? "?key=" + llmApiKey : ""))
                .headers(headers -> {
                    if (provider.equals("cisco") || provider.equals("openai")) {
                        // Both Cisco LLM Proxy and OpenAI use Bearer token auth
                        headers.setBearerAuth(llmApiKey);
                    } else if (provider.equals("anthropic")) {
                        headers.set("x-api-key", llmApiKey);
                        headers.set("anthropic-version", "2023-06-01");
                    }
                })
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();

        log.debug("LLM Response: {}", response);
        return extractContent(response);
    }

    private Map<String, Object> buildOpenAIRequest(String prompt) {
        return Map.of(
                "model", llmModel,
                "messages", List.of(
                        Map.of("role", "system", "content", "You are an expert at selecting the right tools for AI agents. Always respond with valid JSON."),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.3,
                "max_tokens", 1000
        );
    }

    private Map<String, Object> buildAnthropicRequest(String prompt) {
        return Map.of(
                "model", llmModel,
                "max_tokens", 1000,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );
    }

    private Map<String, Object> buildGeminiRequest(String prompt) {
        return Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.3,
                        "maxOutputTokens", 1000
                )
        );
    }

    private String extractContent(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);

        switch (llmProvider.toLowerCase()) {
            case "cisco":
            case "openai":
                // Cisco LLM Proxy uses OpenAI-compatible response format
                return root.path("choices").get(0).path("message").path("content").asText();
            case "anthropic":
                return root.path("content").get(0).path("text").asText();
            case "gemini":
                return root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
            default:
                return response;
        }
    }

    private LLMAnalysisResult parseAnalysisResponse(String response) {
        try {
            // Extract JSON from response (in case there's extra text)
            String json = extractJson(response);
            JsonNode root = objectMapper.readTree(json);

            List<String> selectedTools = new ArrayList<>();
            if (root.has("selectedTools") && root.get("selectedTools").isArray()) {
                for (JsonNode tool : root.get("selectedTools")) {
                    selectedTools.add(tool.asText());
                }
            }

            List<String> permissions = new ArrayList<>();
            if (root.has("permissions") && root.get("permissions").isArray()) {
                for (JsonNode perm : root.get("permissions")) {
                    permissions.add(perm.asText());
                }
            }

            List<String> expectedInputs = new ArrayList<>();
            if (root.has("expectedInputs") && root.get("expectedInputs").isArray()) {
                for (JsonNode input : root.get("expectedInputs")) {
                    expectedInputs.add(input.asText());
                }
            }

            return LLMAnalysisResult.builder()
                    .selectedTools(selectedTools)
                    .goal(root.path("goal").asText())
                    .executionMode(root.path("executionMode").asText("static"))
                    .permissions(permissions)
                    .expectedInputs(expectedInputs)
                    .reasoning(root.path("reasoning").asText())
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse LLM response: {}", e.getMessage());
            return null;
        }
    }

    private String extractJson(String text) {
        // Find JSON object in text
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    /**
     * Build tool arguments using LLM intelligence.
     * Translates natural language queries into precise API call arguments.
     * This eliminates the need for hardcoded if-else patterns.
     */
    public Map<String, Object> buildToolArguments(String toolName, String query, String toolDescription) {
        if (!isEnabled()) {
            log.debug("LLM is disabled, cannot build arguments");
            return null;
        }

        if (query == null || query.isBlank()) {
            log.warn("Empty query provided for argument building");
            return null;
        }

        try {
            log.info("Building arguments with LLM for tool: {} with query: '{}'", toolName, query);
            String prompt = buildArgumentPrompt(toolName, query, toolDescription);
            log.debug("LLM Prompt:\n{}", prompt);
            
            String response = callLLM(prompt);
            log.debug("LLM Response: {}", response);
            
            Map<String, Object> arguments = parseArgumentResponse(response);
            
            if (arguments == null || arguments.isEmpty()) {
                log.warn("LLM returned empty arguments for query: '{}'", query);
                return null;
            }
            
            log.info("LLM generated arguments: {}", arguments);
            return arguments;
            
        } catch (Exception e) {
            log.error("LLM argument building failed for '{}': {}", query, e.getMessage());
            return null;
        }
    }

    private String buildArgumentPrompt(String toolName, String query, String toolDescription) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert API argument builder. Translate natural language queries into precise API calls.\n\n");
        prompt.append("Tool: ").append(toolName).append("\n");
        prompt.append("Tool Description: ").append(toolDescription != null ? toolDescription : "API call tool").append("\n");
        prompt.append("User Query: ").append(query).append("\n\n");
        
        // Add tool-specific comprehensive hints
        if (toolName.contains("jira") && toolName.contains("rest_api")) {
            prompt.append("=== JIRA REST API Reference ===\n\n");
            
            prompt.append("1. GET SINGLE ISSUE:\n");
            prompt.append("   endpoint: 'issue/{ISSUE_KEY}'  (e.g., 'issue/CAI-6675')\n");
            prompt.append("   method: 'GET'\n\n");
            
            prompt.append("2. SEARCH ISSUES (with JQL):\n");
            prompt.append("   endpoint: 'search'\n");
            prompt.append("   method: 'GET'\n");
            prompt.append("   params: { \"jql\": \"<JQL query>\", \"maxResults\": 50 }\n\n");
            
            prompt.append("   Common JQL patterns:\n");
            prompt.append("   - My open issues: assignee = currentUser() AND status != Done\n");
            prompt.append("   - My issues: assignee = currentUser()\n");
            prompt.append("   - Open bugs: issuetype = Bug AND status != Done\n");
            prompt.append("   - Current sprint: sprint in openSprints()\n");
            prompt.append("   - Project issues: project = 'PROJECT_KEY'\n");
            prompt.append("   - Recently updated: updated >= -7d\n");
            prompt.append("   - High priority: priority in (Highest, High)\n");
            prompt.append("   - Assigned to user: assignee = 'username'\n");
            prompt.append("   - Text search: text ~ 'search term'\n\n");
            
            prompt.append("3. ADD COMMENT:\n");
            prompt.append("   endpoint: 'issue/{ISSUE_KEY}/comment'\n");
            prompt.append("   method: 'POST'\n");
            prompt.append("   data: { \"body\": \"Comment text here\" }\n\n");
            
            prompt.append("4. UPDATE ISSUE:\n");
            prompt.append("   endpoint: 'issue/{ISSUE_KEY}'\n");
            prompt.append("   method: 'PUT'\n");
            prompt.append("   data: { \"fields\": { \"summary\": \"...\", \"description\": \"...\" } }\n\n");
            
            prompt.append("5. ASSIGN ISSUE:\n");
            prompt.append("   endpoint: 'issue/{ISSUE_KEY}'\n");
            prompt.append("   method: 'PUT'\n");
            prompt.append("   data: { \"fields\": { \"assignee\": { \"name\": \"username\" } } }\n\n");
            
            prompt.append("6. GET TRANSITIONS (for status change):\n");
            prompt.append("   endpoint: 'issue/{ISSUE_KEY}/transitions'\n");
            prompt.append("   method: 'GET'\n\n");
            
        } else if (toolName.contains("webex")) {
            prompt.append("=== WEBEX API Reference ===\n\n");
            
            prompt.append("IMPORTANT: Webex requires 'spaceId' (a UUID starting with 'Y2lz...'), NOT space names!\n\n");
            
            prompt.append("For list_spaces: {} (no params, returns user's spaces)\n");
            prompt.append("For get_space: { \"spaceId\": \"Y2lzY29zcGFyazovL...\" }\n");
            prompt.append("For list_messages: { \"spaceId\": \"Y2lzY29zcGFyazovL...\", \"max\": 20 }\n");
            prompt.append("For post_message: { \"spaceId\": \"Y2lzY29zcGFyazovL...\", \"markdown\": \"message text\" }\n");
            prompt.append("   - spaceId is REQUIRED (UUID format)\n");
            prompt.append("   - markdown is REQUIRED (the message content)\n");
            prompt.append("   - If user provides space NAME (not ID), extract the message and set spaceId to 'SPACE_NAME:spacename'\n");
            prompt.append("For ask_space: { \"spaceId\": \"Y2lzY29zcGFyazovL...\", \"question\": \"user question\" }\n\n");
            
            prompt.append("Example: 'Post hello in My Team space' →\n");
            prompt.append("  { \"spaceId\": \"SPACE_NAME:My Team\", \"markdown\": \"hello\" }\n\n");
            
        } else if (toolName.contains("confluence")) {
            prompt.append("=== CONFLUENCE API Reference ===\n\n");
            
            prompt.append("For search: { \"cql_query\": \"text ~ 'search term'\", \"limit\": 25 }\n");
            prompt.append("For get page by ID: { \"page_id\": \"12345\" }\n");
            prompt.append("For get page by title: { \"space_key\": \"SPACE\", \"title\": \"Page Title\" }\n\n");
            
        } else if (toolName.contains("github")) {
            prompt.append("=== GITHUB API Reference ===\n\n");
            
            prompt.append("For search: { \"resource\": \"code\", \"parameters\": { \"q\": \"search term\" } }\n");
            prompt.append("For PR diff: { \"owner\": \"org\", \"repo\": \"repo-name\", \"pull_number\": 123 }\n\n");
        }
        
        prompt.append("=== YOUR TASK ===\n");
        prompt.append("Analyze the user query and generate the correct API arguments.\n");
        prompt.append("Extract any issue keys (like ABC-123), usernames, search terms, etc. from the query.\n\n");
        
        prompt.append("Respond with ONLY a valid JSON object:\n");
        prompt.append("{\n");
        prompt.append("  \"endpoint\": \"the API endpoint path\",\n");
        prompt.append("  \"method\": \"GET or POST or PUT or DELETE\",\n");
        prompt.append("  \"params\": { ... },  // for GET query parameters\n");
        prompt.append("  \"data\": { ... }     // for POST/PUT request body\n");
        prompt.append("}\n\n");
        prompt.append("IMPORTANT: Respond with valid JSON only, no explanations or markdown.");
        
        return prompt.toString();
    }

    private Map<String, Object> parseArgumentResponse(String response) {
        try {
            String json = extractJson(response);
            JsonNode root = objectMapper.readTree(json);
            
            Map<String, Object> arguments = new HashMap<>();
            
            if (root.has("endpoint")) {
                arguments.put("endpoint", root.get("endpoint").asText());
            }
            if (root.has("method")) {
                arguments.put("method", root.get("method").asText());
            }
            if (root.has("data")) {
                arguments.put("data", objectMapper.convertValue(root.get("data"), Map.class));
            }
            if (root.has("params")) {
                arguments.put("params", objectMapper.convertValue(root.get("params"), Map.class));
            }
            
            return arguments;
        } catch (Exception e) {
            log.error("Failed to parse LLM argument response: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Result from LLM analysis
     */
    @lombok.Data
    @lombok.Builder
    public static class LLMAnalysisResult {
        private List<String> selectedTools;
        private String goal;
        private String executionMode;
        private List<String> permissions;
        private List<String> expectedInputs;
        private String reasoning;
    }
}


