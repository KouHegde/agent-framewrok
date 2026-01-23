package com.agentframework.registry;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MCPTool {
    private String name;
    private String description;
    private String category;
    private List<String> capabilities;
    private List<String> requiredInputs;
}

