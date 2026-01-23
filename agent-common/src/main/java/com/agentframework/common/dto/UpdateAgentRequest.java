package com.agentframework.common.dto;

import java.util.List;

public record UpdateAgentRequest(
        String userConfig,
        List<String> mcpServerNames
) {}
