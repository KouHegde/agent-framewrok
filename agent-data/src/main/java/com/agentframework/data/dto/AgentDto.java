package com.agentframework.data.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Data transfer object for Agent.
 * Used by the facade to return agent data without exposing entities.
 */
public record AgentDto(
        UUID id,
        String botId,
        String userId,
        String userConfig,
        List<String> mcpServerNames,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
