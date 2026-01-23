package com.agentframework.service;

import com.agentframework.common.dto.AgentConfigDto;
import com.agentframework.dto.AgentSpec;
import com.agentframework.dto.ContentAnalysis;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service that dynamically decides agent configuration based on:
 * - Fetched MCP data (size, structure, content)
 * - User's agent description/prompt
 * - MCP servers being used
 */
@Slf4j
@Service
public class ConfigDeciderService {

    // Size thresholds in bytes
    private static final long SMALL_DATA_THRESHOLD = 100 * 1024;      // 100 KB
    private static final long MEDIUM_DATA_THRESHOLD = 1024 * 1024;    // 1 MB
    private static final long LARGE_DATA_THRESHOLD = 3 * 1024 * 1024; // 3 MB

    /**
     * Analyzes the context and decides optimal agent configuration.
     *
     * @param agentSpec       The agent specification (from MetaAgentService)
     * @param mcpData         Map of MCP server name to fetched data
     * @param userDescription The original user description/prompt
     * @return Optimal configuration for the agent
     */
    public AgentConfigDto decideConfig(AgentSpec agentSpec, Map<String, Object> mcpData, String userDescription) {
        log.info("Deciding config for agent: {}", agentSpec.getAgentName());

        // Calculate total data size
        long totalDataSize = calculateDataSize(mcpData);
        log.debug("Total MCP data size: {} bytes", totalDataSize);

        // Analyze content
        ContentAnalysis contentAnalysis = analyzeContent(mcpData, userDescription);
        log.debug("Content analysis: {}", contentAnalysis);

        // Decide each configuration parameter
        List<String> ragScope = decideRagScope(mcpData, contentAnalysis, userDescription);
        String reasoningStyle = decideReasoningStyle(contentAnalysis, totalDataSize, userDescription);
        BigDecimal temperature = decideTemperature(contentAnalysis, reasoningStyle);
        String retrieverType = decideRetrieverType(reasoningStyle, totalDataSize);
        int retrieverK = decideRetrieverK(totalDataSize, reasoningStyle);
        String executionMode = decideExecutionMode(agentSpec, contentAnalysis);
        List<String> permissions = decidePermissions(userDescription, agentSpec);

        AgentConfigDto config = new AgentConfigDto(
                ragScope,
                reasoningStyle,
                temperature,
                retrieverType,
                retrieverK,
                executionMode,
                permissions
        );

        log.info("Decided config: ragScope={}, reasoningStyle={}, temperature={}, retrieverType={}, retrieverK={}, executionMode={}",
                ragScope, reasoningStyle, temperature, retrieverType, retrieverK, executionMode);

        return config;
    }

    // ==================== RAG SCOPE DECISION ====================

    private List<String> decideRagScope(Map<String, Object> mcpData, ContentAnalysis analysis, String description) {
        Set<String> scopes = new HashSet<>();
        String lowerDesc = description.toLowerCase();

        // 1. Source-based scope: Add scopes based on MCP servers used
        for (String mcpServer : mcpData.keySet()) {
            scopes.addAll(getDefaultScopesForMcp(mcpServer));
        }

        // 2. Keyword-based scope: Add scopes based on description keywords
        if (containsAny(lowerDesc, "compliance", "audit", "policy", "regulation", "security")) {
            scopes.add("policy_docs");
            scopes.add("audit_logs");
        }
        if (containsAny(lowerDesc, "ticket", "bug", "issue", "sprint", "backlog")) {
            scopes.add("tickets");
        }
        if (containsAny(lowerDesc, "documentation", "wiki", "runbook", "guide", "how-to")) {
            scopes.add("knowledge_base");
        }
        if (containsAny(lowerDesc, "code", "pr", "pull request", "commit", "review", "diff")) {
            scopes.add("code_artifacts");
        }
        if (containsAny(lowerDesc, "incident", "outage", "error", "failure", "alert")) {
            scopes.add("incident_reports");
            scopes.add("runbooks");
        }

        // 3. Content-based scope: Add based on detected content types
        if (analysis.isHasStructuredData()) {
            scopes.add("structured_data");
        }
        if (analysis.isHasTimeSeries()) {
            scopes.add("time_series");
        }

        return new ArrayList<>(scopes);
    }

    private List<String> getDefaultScopesForMcp(String mcpServer) {
        String lowerServer = mcpServer.toLowerCase();

        if (lowerServer.contains("jira")) {
            return List.of("tickets", "sprint_data");
        }
        if (lowerServer.contains("confluence")) {
            return List.of("policy_docs", "knowledge_base");
        }
        if (lowerServer.contains("github")) {
            return List.of("code_artifacts", "pr_reviews");
        }

        return List.of();
    }

    // ==================== REASONING STYLE DECISION ====================

    private String decideReasoningStyle(ContentAnalysis analysis, long dataSize, String description) {
        String lowerDesc = description.toLowerCase();

        // 1. Investigation/root cause → step-by-step
        if (containsAny(lowerDesc, "investigate", "root cause", "why", "analyze", "debug", "troubleshoot")) {
            return "step-by-step";
        }

        // 2. Comparison tasks → comparative
        if (containsAny(lowerDesc, "compare", "difference", "vs", "versus", "between")) {
            return "comparative";
        }

        // 3. Aggregation/summary → analytical
        if (containsAny(lowerDesc, "summary", "summarize", "count", "total", "aggregate", "report", "metrics")) {
            return "analytical";
        }

        // 4. Planning/how-to → structured
        if (containsAny(lowerDesc, "how to", "steps to", "plan", "guide", "procedure")) {
            return "structured";
        }

        // 5. Large/complex data → step-by-step
        if (dataSize > MEDIUM_DATA_THRESHOLD || analysis.getSourceCount() > 2) {
            return "step-by-step";
        }

        // 6. Multi-source data → step-by-step
        if (analysis.getSourceCount() > 1) {
            return "step-by-step";
        }

        // 7. Simple lookups → direct
        if (containsAny(lowerDesc, "find", "get", "show", "list", "what is")) {
            return "direct";
        }

        // Default
        return "direct";
    }

    // ==================== TEMPERATURE DECISION ====================

    private BigDecimal decideTemperature(ContentAnalysis analysis, String reasoningStyle) {
        // Base temperature by domain
        BigDecimal baseTemp;

        if (analysis.isAuditDomain()) {
            baseTemp = new BigDecimal("0.15");  // Very precise for audit/compliance
        } else if (analysis.isAnalysisDomain()) {
            baseTemp = new BigDecimal("0.25");  // Precise for analysis
        } else if (analysis.isCodeDomain()) {
            baseTemp = new BigDecimal("0.30");  // Balanced for code
        } else {
            baseTemp = new BigDecimal("0.40");  // Default balanced
        }

        // Adjust by reasoning style
        switch (reasoningStyle) {
            case "step-by-step":
                baseTemp = baseTemp.subtract(new BigDecimal("0.05"));
                break;
            case "analytical":
                baseTemp = baseTemp.subtract(new BigDecimal("0.05"));
                break;
            case "comparative":
                // Keep as is
                break;
            case "structured":
                baseTemp = baseTemp.add(new BigDecimal("0.05"));
                break;
        }

        // Clamp to valid range
        if (baseTemp.compareTo(new BigDecimal("0.10")) < 0) {
            baseTemp = new BigDecimal("0.10");
        }
        if (baseTemp.compareTo(new BigDecimal("0.90")) > 0) {
            baseTemp = new BigDecimal("0.90");
        }

        return baseTemp;
    }

    // ==================== RETRIEVER TYPE DECISION ====================

    private String decideRetrieverType(String reasoningStyle, long dataSize) {
        // Step-by-step reasoning needs multi-query to break down complex queries
        if ("step-by-step".equals(reasoningStyle)) {
            return "multi-query";
        }

        // Comparative needs ensemble to get diverse results
        if ("comparative".equals(reasoningStyle)) {
            return "ensemble";
        }

        // Large data benefits from multi-query
        if (dataSize > MEDIUM_DATA_THRESHOLD) {
            return "multi-query";
        }

        // Small data with simple queries → simple retriever
        if (dataSize < SMALL_DATA_THRESHOLD) {
            return "simple";
        }

        // Default for medium data
        return "simple";
    }

    // ==================== RETRIEVER K DECISION ====================

    private int decideRetrieverK(long dataSize, String reasoningStyle) {
        int baseK;

        // Base K by data size
        if (dataSize < SMALL_DATA_THRESHOLD) {
            baseK = 3;  // Small data, few chunks needed
        } else if (dataSize < MEDIUM_DATA_THRESHOLD) {
            baseK = 5;  // Medium data
        } else if (dataSize < LARGE_DATA_THRESHOLD) {
            baseK = 10; // Large data
        } else {
            baseK = 15; // Very large data
        }

        // Adjust by reasoning style
        switch (reasoningStyle) {
            case "step-by-step":
                baseK += 3;  // Need more context for thorough analysis
                break;
            case "comparative":
                baseK += 5;  // Need more chunks to compare
                break;
            case "analytical":
                baseK += 2;  // Need good coverage
                break;
            case "direct":
                // Keep as is - minimal needed
                break;
        }

        // Clamp to valid range (1-50)
        return Math.max(1, Math.min(50, baseK));
    }

    // ==================== EXECUTION MODE DECISION ====================

    private String decideExecutionMode(AgentSpec agentSpec, ContentAnalysis analysis) {
        // Already decided by MetaAgentService
        if (agentSpec.getExecutionMode() != null) {
            return agentSpec.getExecutionMode();
        }

        // Multi-source or complex analysis → dynamic
        if (analysis.getSourceCount() > 1 || analysis.isComplexQuery()) {
            return "dynamic";
        }

        return "static";
    }

    // ==================== PERMISSIONS DECISION ====================

    private List<String> decidePermissions(String description, AgentSpec agentSpec) {
        List<String> permissions = new ArrayList<>();
        String lowerDesc = description.toLowerCase();

        // Check for write indicators
        if (containsAny(lowerDesc, "create", "add", "update", "modify", "delete", "remove", "change", "edit")) {
            permissions.add("write");
        }

        // Check for execute indicators
        if (containsAny(lowerDesc, "run", "execute", "deploy", "trigger", "start", "stop")) {
            permissions.add("execute");
        }

        // Always add read
        if (permissions.isEmpty()) {
            permissions.add("read_only");
        } else {
            permissions.add(0, "read");  // Read is implicit with write
        }

        // Override with AgentSpec permissions if specified
        if (agentSpec.getPermissions() != null && !agentSpec.getPermissions().isEmpty()) {
            return agentSpec.getPermissions();
        }

        return permissions;
    }

    // ==================== CONTENT ANALYSIS ====================

    private ContentAnalysis analyzeContent(Map<String, Object> mcpData, String description) {
        ContentAnalysis analysis = new ContentAnalysis();
        analysis.setSourceCount(mcpData.size());

        String lowerDesc = description.toLowerCase();

        // Domain detection
        analysis.setAuditDomain(containsAny(lowerDesc, "audit", "compliance", "security", "policy", "regulation"));
        analysis.setAnalysisDomain(containsAny(lowerDesc, "analyze", "investigation", "root cause", "debug"));
        analysis.setCodeDomain(containsAny(lowerDesc, "code", "pr", "commit", "review", "github"));

        // Query complexity
        analysis.setComplexQuery(containsAny(lowerDesc, "correlate", "pattern", "anomaly", "trend", "multiple"));

        // Content type detection (simplified - in real impl, would parse mcpData)
        analysis.setHasStructuredData(mcpData.values().stream()
                .anyMatch(data -> data instanceof Map || data instanceof List));
        analysis.setHasTimeSeries(containsAny(lowerDesc, "time", "period", "last", "recent", "trend", "history"));

        return analysis;
    }

    private long calculateDataSize(Map<String, Object> mcpData) {
        // Estimate size by converting to string (simplified)
        // In real implementation, would calculate actual byte size
        return mcpData.values().stream()
                .mapToLong(data -> {
                    if (data == null) return 0;
                    if (data instanceof String) return ((String) data).length();
                    if (data instanceof byte[]) return ((byte[]) data).length;
                    return data.toString().length();
                })
                .sum();
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

}
