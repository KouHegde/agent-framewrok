package com.agentframework.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class RunAgentRequest {
    private String query;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("max_iterations")
    private Integer maxIterations = 10;

    @JsonProperty("auto_approve")
    private Boolean autoApprove = false;
}
