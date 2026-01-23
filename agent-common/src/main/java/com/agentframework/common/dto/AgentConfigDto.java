package com.agentframework.common.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for agent configuration updates.
 * Used when updating RAG/reasoning/retriever settings.
 */
public record AgentConfigDto(
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
        List<String> permissions
) {
    /**
     * Builder-style factory for creating config with defaults.
     */
    public static AgentConfigDto withDefaults() {
        return new AgentConfigDto(
                List.of(),
                "direct",
                new BigDecimal("0.30"),
                "simple",
                5,
                "static",
                List.of("read_only")
        );
    }
}
