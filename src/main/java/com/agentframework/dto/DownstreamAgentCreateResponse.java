package com.agentframework.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DownstreamAgentCreateResponse {
    private String agentId;
    private String status;
}
