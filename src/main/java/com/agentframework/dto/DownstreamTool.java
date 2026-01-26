package com.agentframework.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DownstreamTool {
    private String name;
    private String description;
    @JsonProperty("requires_approval")
    private Boolean requiresApproval;
}
