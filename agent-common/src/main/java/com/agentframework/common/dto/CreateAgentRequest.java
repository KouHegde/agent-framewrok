package com.agentframework.common.dto;

import java.util.List;

public record CreateAgentRequest(
        String userId,
        String botId,
        String userConfig,
        List<String> mcpServerNames
) {}
