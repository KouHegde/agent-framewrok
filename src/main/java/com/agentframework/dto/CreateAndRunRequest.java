package com.agentframework.dto;

import lombok.Data;

import java.util.Map;

@Data
public class CreateAndRunRequest {
    private String name;
    private String description;
    private String query;  // Optional - defaults to description
    private Map<String, Object> inputs;  // Optional - runtime inputs
}
