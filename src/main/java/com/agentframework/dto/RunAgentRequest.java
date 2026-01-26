package com.agentframework.dto;

import lombok.Data;

import java.util.Map;

@Data
public class RunAgentRequest {
    private String query;
    private Map<String, Object> inputs;
}
