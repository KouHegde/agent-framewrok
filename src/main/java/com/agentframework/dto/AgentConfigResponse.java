package com.agentframework.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentConfigResponse {
    private String agentId;
    private List<String> ragScope;
    private String reasoningStyle;
    private BigDecimal temperature;
    private String retrieverType;
    private Integer retrieverK;
    private String executionMode;
    private List<String> permissions;
}
