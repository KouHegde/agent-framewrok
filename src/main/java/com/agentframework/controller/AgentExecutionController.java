package com.agentframework.controller;

import com.agentframework.dto.DownstreamAgentRunRequest;
import com.agentframework.dto.DownstreamAgentRunResponse;
import com.agentframework.dto.ErrorResponse;
import com.agentframework.dto.RunAgentRequest;
import com.agentframework.facade.AgentFacade;
import com.agentframework.service.DownstreamAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentExecutionController {

    private final AgentFacade agentFacade;
    private final DownstreamAgentService downstreamAgentService;

    /**
     * Run an agent by ID.
     * Validates agent exists and calls the downstream service.
     */
    @PostMapping("/{id}/run")
    public ResponseEntity<?> runAgentById(
            @PathVariable("id") UUID agentId,
            @RequestBody RunAgentRequest request) {

        // Validate agent exists
        var agentOpt = agentFacade.findAgentById(agentId);
        if (agentOpt.isEmpty()) {
            log.warn("Agent not found: {}", agentId);
            return ResponseEntity.notFound().build();
        }

        var agent = agentOpt.get();

        // Validate downstream agent ID exists
        if (agent.downstreamAgentId() == null || agent.downstreamAgentId().isBlank()) {
            log.error("Agent {} has no downstream agent ID", agentId);
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("Agent not ready", "Agent has no downstream agent ID configured")
            );
        }

        // Validate query is provided
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("Invalid request", "Query is required")
            );
        }

        log.info("Running agent: {} ({}) with downstream ID: {}",
                agent.name(), agentId, agent.downstreamAgentId());

        try {
            // Build downstream request
            DownstreamAgentRunRequest downstreamRequest = new DownstreamAgentRunRequest();
            downstreamRequest.setAgentId(agent.downstreamAgentId());
            downstreamRequest.setQuery(request.getQuery());
            downstreamRequest.setUserId(request.getUserId());
            downstreamRequest.setMaxIterations(request.getMaxIterations());
            downstreamRequest.setAutoApprove(request.getAutoApprove());

            // Call downstream service
            DownstreamAgentRunResponse response = downstreamAgentService.runAgent(downstreamRequest);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to run agent {}: {}", agentId, e.getMessage());
            return ResponseEntity.internalServerError().body(
                    new ErrorResponse("Failed to run agent", e.getMessage())
            );
        }
    }
}
