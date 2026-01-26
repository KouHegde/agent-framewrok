package com.agentframework.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolRequest {
    private String name;
    private String description;
    private String category;
    private List<String> capabilities;

    @JsonProperty("required_inputs")
    private List<String> requiredInputs;
}
