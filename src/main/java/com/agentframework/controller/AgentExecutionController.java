package com.agentframework.controller;

import com.agentframework.dto.DownstreamAgentRunRequest;
import com.agentframework.dto.DownstreamAgentRunResponse;
import com.agentframework.dto.ErrorResponse;
import com.agentframework.dto.RunAgentRequest;
import com.agentframework.facade.AgentFacade;
import com.agentframework.service.DownstreamAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@Tag(name = "Agent Execution", description = "Run agents and manage sessions")
public class AgentExecutionController {

    private final AgentFacade agentFacade;
    private final DownstreamAgentService downstreamAgentService;

    /**
     * Run an agent by ID.
     * Validates agent exists, manages sessions, and calls the downstream service.
     *
     * Session rules:
     * - First request: Creates a new session and stores it
     * - Subsequent requests: Must use the same session ID or will be rejected
     * - If is_last_session=true: Clears the session after request completes
     *
     * Requires: agents:execute scope
     */
    @Operation(summary = "Run agent", description = "Execute an agent with a query. Requires agents:execute scope.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Agent executed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or agent not ready"),
            @ApiResponse(responseCode = "404", description = "Agent not found"),
            @ApiResponse(responseCode = "409", description = "Session conflict"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    @PostMapping("/{id}/run")
    @PreAuthorize("@scopeChecker.hasScope(authentication, 'agents:execute')")
    public ResponseEntity<?> runAgentById(
            @Parameter(description = "Agent ID") @PathVariable("id") UUID agentId,
            @RequestBody RunAgentRequest request) {

        // Validate agent exists
        var agentOpt = agentFacade.findAgentById(agentId);
        if (agentOpt.isEmpty()) {
            log.warn("Agent not found: {}", agentId);
            return ResponseEntity.notFound().build();
        }

        var agent = agentOpt.get();
        String downstreamAgentId = agent.downstreamAgentId();

        // Validate downstream agent ID exists
        if (downstreamAgentId == null || downstreamAgentId.isBlank()) {
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

        // Session management
        String activeSession = downstreamAgentService.getActiveSession(downstreamAgentId);
        String sessionIdToUse;

        if (activeSession == null) {
            // No active session - create a new one
            sessionIdToUse = downstreamAgentService.getOrCreateSession(downstreamAgentId);
            log.info("Created new session for agent {}: {}", agentId, sessionIdToUse);
        } else {
            // Active session exists - validate incoming session ID
            String incomingSessionId = request.getSessionId();

            if (incomingSessionId == null || !incomingSessionId.equals(activeSession)) {
                log.warn("Session conflict for agent {}. Active: {}, Incoming: {}",
                        agentId, activeSession, incomingSessionId);
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        new ErrorResponse("Session conflict",
                                "Agent has an active session. Use session_id: " + activeSession
                                        + " or set is_last_session=true to end the current session first.")
                );
            }
            sessionIdToUse = activeSession;
            log.info("Using existing session for agent {}: {}", agentId, sessionIdToUse);
        }

        log.info("Running agent: {} ({}) with downstream ID: {}, session: {}",
                agent.name(), agentId, downstreamAgentId, sessionIdToUse);

        try {
            // Build downstream request
            DownstreamAgentRunRequest downstreamRequest = new DownstreamAgentRunRequest();
            downstreamRequest.setAgentId(downstreamAgentId);
            downstreamRequest.setQuery(request.getQuery());
            downstreamRequest.setUserId(request.getUserId());
            downstreamRequest.setSessionId(sessionIdToUse);
            downstreamRequest.setMaxIterations(request.getMaxIterations());
            downstreamRequest.setAutoApprove(request.getAutoApprove());

            // Call downstream service
            DownstreamAgentRunResponse response = downstreamAgentService.runAgent(downstreamRequest);

            // Check if downstream returned an error/failure status
            if (isErrorStatus(response.getStatus())) {
                downstreamAgentService.clearSession(downstreamAgentId);
                log.info("Cleared session for agent {} due to downstream error status: {}",
                        agentId, response.getStatus());
            }
            // If is_last_session flag is set, clear the session
            else if (Boolean.TRUE.equals(request.getIsLastSession())) {
                downstreamAgentService.clearSession(downstreamAgentId);
                log.info("Cleared session for agent {} (is_last_session=true)", agentId);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            // Clear session on any exception/failure
            downstreamAgentService.clearSession(downstreamAgentId);
            log.error("Failed to run agent {}, cleared session: {}", agentId, e.getMessage());
            return ResponseEntity.internalServerError().body(
                    new ErrorResponse("Failed to run agent", e.getMessage())
            );
        }
    }

    /**
     * Clear the active session for an agent.
     * Allows starting a fresh conversation.
     * Requires: sessions:write scope
     */
    @Operation(summary = "Clear agent session", description = "Clear the active conversation session. Requires sessions:write scope.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Session cleared successfully"),
            @ApiResponse(responseCode = "404", description = "Agent not found"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    @DeleteMapping("/{id}/session")
    @PreAuthorize("@scopeChecker.hasScope(authentication, 'sessions:write')")
    public ResponseEntity<?> clearSession(
            @Parameter(description = "Agent ID") @PathVariable("id") UUID agentId) {
        var agentOpt = agentFacade.findAgentById(agentId);
        if (agentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var agent = agentOpt.get();
        if (agent.downstreamAgentId() != null) {
            downstreamAgentService.clearSession(agent.downstreamAgentId());
            log.info("Cleared session for agent: {} ({})", agent.name(), agentId);
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Check if the downstream response status indicates an error.
     */
    private boolean isErrorStatus(String status) {
        if (status == null) {
            return false;
        }
        String lowerStatus = status.toLowerCase();
        return lowerStatus.contains("error")
                || lowerStatus.contains("fail")
                || lowerStatus.contains("exception");
    }
}
