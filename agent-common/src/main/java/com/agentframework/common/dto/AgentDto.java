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
        UUID id,
        String botId,
        String userId,
        
        // Agent description/goal
        String description,
        String goal,
        
        // MCP Servers
        List<String> mcpServerNames,
        
        // RAG Configuration
        List<String> ragScope,
        
        // Reasoning Configuration
        String reasoningStyle,
        BigDecimal temperature,
        
        // Retriever Configuration
        String retrieverType,
        Integer retrieverK,
        
        // Execution Configuration
        String executionMode,
        List<String> permissions,
        
        // Timestamps
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
