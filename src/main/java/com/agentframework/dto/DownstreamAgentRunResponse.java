package com.agentframework.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DownstreamAgentRunResponse {

    private String status;

    @JsonProperty("agent_id")
    private String agentId;

    @JsonProperty("agent_name")
    private String agentName;

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("stream_viewer_url")
    private String streamViewerUrl;

    @JsonProperty("final_response")
    private String finalResponse;

    private List<String> plan;

    @JsonProperty("tool_executions")
    private List<Object> toolExecutions;

    @JsonProperty("iterations_used")
    private Integer iterationsUsed;

    @JsonProperty("total_duration_ms")
    private Long totalDurationMs;

    private String error;

    @JsonProperty("pending_approval")
    private Object pendingApproval;
}
