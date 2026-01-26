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
public class DownstreamAgentCreateRequest {
    private String name;
    private String purpose;
    private String description;
    @JsonProperty("owner_id")
    private String ownerId;
    @JsonProperty("tenant_id")
    private String tenantId;
    @JsonProperty("system_prompt")
    private String systemPrompt;
    private List<DownstreamTool> tools;
    private List<DownstreamPolicy> policies;
    @JsonProperty("max_steps")
    private Integer maxSteps;
    private BigDecimal temperature;
    @JsonProperty("rag_scope")
    private List<String> ragScope;
    @JsonProperty("reasoning_style")
    private String reasoningStyle;
    @JsonProperty("retriever_type")
    private String retrieverType;
    @JsonProperty("retriever_k")
    private Integer retrieverK;
    @JsonProperty("execution_mode")
    private String executionMode;
    private List<String> permissions;
}
