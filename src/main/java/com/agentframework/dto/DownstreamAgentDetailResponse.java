package com.agentframework.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DownstreamAgentDetailResponse {
    private String id;
    private String name;
    private String description;
    private String purpose;
    @JsonProperty("owner_id")
    private String ownerId;
    @JsonProperty("tenant_id")
    private String tenantId;
    private String status;
    @JsonProperty("system_prompt")
    private String systemPrompt;
    @JsonProperty("max_steps")
    private Integer maxSteps;
    private BigDecimal temperature;
    @JsonProperty("created_at")
    private String createdAt;
    @JsonProperty("updated_at")
    private String updatedAt;
    @JsonProperty("last_invoked_at")
    private String lastInvokedAt;
    @JsonProperty("invocation_count")
    private Integer invocationCount;
    private List<DownstreamToolDetailResponse> tools;
    private List<DownstreamPolicyDetailResponse> policies;
}
