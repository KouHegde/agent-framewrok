package com.agentframework.common.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Data transfer object for Agent.
 * Shared between data and service modules.
 */
public record AgentDto(
        // Core Info
        UUID id,
        String name,
        String description,
        String goal,
        
        // Lookup Key
        String allowedTools,  // Sorted comma-separated
        
        // Full Spec
        String agentSpec,     // JSON string
        
        // Ownership
        String createdBy,
        String tenantId,
        
        // MCP Servers
        List<String> mcpServerNames,
        
        // RAG Configuration
        String ragScope,
        
        // Reasoning Configuration
        String reasoningStyle,
        BigDecimal temperature,
        
        // Retriever Configuration
        String retrieverType,
        Integer retrieverK,
        
        // Execution Configuration
        String executionMode,
        String permissions,
        
        // Python AgentBrain Response
        String systemPrompt,
        Integer maxSteps,
        String brainAgentId,
        
        // Status
        String status,

        // Downstream status
        String downstreamStatus,
        String downstreamAgentId,
        
        // Timestamps
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
