package com.agentframework.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DownstreamAgentListResponse {
    private List<DownstreamAgentDetailResponse> agents;
    private Integer total;
}
