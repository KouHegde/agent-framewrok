package com.agentframework.common.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Data transfer object for Agent.
 * Shared between data and service modules.
 */
public record AgentDto(
        UUID id,
        String name,
        String description,
        String agentSpec,  // JSON string of AgentSpec
        String userId,
        String userConfig,
        List<String> mcpServerNames,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
