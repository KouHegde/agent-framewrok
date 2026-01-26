package com.agentframework.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DownstreamPolicyDetailResponse {
    private String id;
    @JsonProperty("policy_type")
    private String policyType;
    @JsonProperty("policy_name")
    private String policyName;
    private Map<String, Object> config;
    @JsonProperty("is_active")
    private Boolean isActive;
}
