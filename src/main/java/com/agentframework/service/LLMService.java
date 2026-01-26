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
     * Build tool arguments using LLM intelligence
     * This eliminates the need for hardcoded if-else patterns
     */
    public Map<String, Object> buildToolArguments(String toolName, String query, String toolDescription) {
        if (!isEnabled()) {
            return null;
        }

        try {
            String prompt = buildArgumentPrompt(toolName, query, toolDescription);
            String response = callLLM(prompt);
            return parseArgumentResponse(response);
        } catch (Exception e) {
            log.error("LLM argument building failed: {}", e.getMessage());
            return null;
        }
    }

    private String buildArgumentPrompt(String toolName, String query, String toolDescription) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an API argument builder. Given a user query and tool description, extract the correct arguments.\n\n");
        prompt.append("Tool: ").append(toolName).append("\n");
        prompt.append("Tool Description: ").append(toolDescription).append("\n");
        prompt.append("User Query: ").append(query).append("\n\n");
        
        // Add tool-specific hints
        if (toolName.contains("jira") && toolName.contains("rest_api")) {
            prompt.append("Jira REST API hints:\n");
            prompt.append("- Get issue: endpoint='issue/{KEY}', method='GET'\n");
            prompt.append("- Add comment: endpoint='issue/{KEY}/comment', method='POST', data={body: 'comment text'}\n");
            prompt.append("- Update issue: endpoint='issue/{KEY}', method='PUT', data={fields: {...}}\n");
            prompt.append("- Search: endpoint='search', method='GET', params={jql: '...'}\n");
            prompt.append("- Transitions: endpoint='issue/{KEY}/transitions', method='GET' or POST with {transition: {id: '...'}}\n\n");
        }
        
        prompt.append("Respond with a JSON object containing the arguments:\n");
        prompt.append("{\n");
        prompt.append("  \"endpoint\": \"the API endpoint\",\n");
        prompt.append("  \"method\": \"GET/POST/PUT/DELETE\",\n");
        prompt.append("  \"data\": { ... } // optional, for POST/PUT\n");
        prompt.append("  \"params\": { ... } // optional, for query params\n");
        prompt.append("}\n\n");
        prompt.append("Extract issue keys, comment text, status names, etc. from the query. Respond with valid JSON only.");
        
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


