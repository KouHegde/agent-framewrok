package com.agentframework.service;

import com.agentframework.dto.DownstreamAgentCreateRequest;
import com.agentframework.dto.DownstreamAgentCreateResponse;
import com.agentframework.dto.DownstreamAgentDetailResponse;
import com.agentframework.dto.DownstreamAgentListResponse;
import com.agentframework.dto.DownstreamAgentRunRequest;
import com.agentframework.dto.DownstreamAgentRunResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownstreamAgentService {

    private final ObjectMapper objectMapper;

    @Value("${downstream.agent.url:http://localhost:8082}")
    private String downstreamAgentUrl;

    /**
     * In-memory session cache: downstreamAgentId -> activeSessionId.
     * For single pod deployment. Consider Redis/DB for multi-pod.
     */
    private final ConcurrentHashMap<String, String> activeSessionCache = new ConcurrentHashMap<>();

    public DownstreamAgentCreateResponse createAgent(DownstreamAgentCreateRequest request) {
        WebClient client = WebClient.builder()
                .baseUrl(downstreamAgentUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();

        try {
            log.info("Creating downstream agent: {}", request.getName());
            log.debug("Downstream create payload: {}", objectMapper.writeValueAsString(request));
            String response = client.post()
                    .uri("/agents")
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response == null || response.isBlank()) {
                throw new IllegalStateException("Downstream returned empty response");
            }

            DownstreamAgentCreateResponse parsed = objectMapper.readValue(response, DownstreamAgentCreateResponse.class);
            log.info("Downstream agent created. Status: {}, ID: {}", parsed.getStatus(), parsed.getAgentId());
            return parsed;
        } catch (Exception e) {
            throw new IllegalStateException("Downstream agent creation failed: " + e.getMessage(), e);
        }
    }

    public DownstreamAgentDetailResponse getAgent(String agentId) {
        WebClient client = WebClient.builder()
                .baseUrl(downstreamAgentUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();

        try {
            String response = client.get()
                    .uri("/agents/" + agentId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response == null || response.isBlank()) {
                throw new IllegalStateException("Downstream returned empty response");
            }

            return objectMapper.readValue(response, DownstreamAgentDetailResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Downstream agent fetch failed: " + e.getMessage(), e);
        }
    }

    public DownstreamAgentListResponse listAgents(String ownerId,
                                                  String tenantId,
                                                  String status,
                                                  Integer limit,
                                                  Integer offset) {
        WebClient client = WebClient.builder()
                .baseUrl(downstreamAgentUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();

        try {
            String uri = UriComponentsBuilder.fromPath("/agents")
                    .queryParamIfPresent("owner_id", java.util.Optional.ofNullable(ownerId))
                    .queryParamIfPresent("tenant_id", java.util.Optional.ofNullable(tenantId))
                    .queryParamIfPresent("status", java.util.Optional.ofNullable(status))
                    .queryParamIfPresent("limit", java.util.Optional.ofNullable(limit))
                    .queryParamIfPresent("offset", java.util.Optional.ofNullable(offset))
                    .toUriString();

            String response = client.get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response == null || response.isBlank()) {
                throw new IllegalStateException("Downstream returned empty response");
            }

            return objectMapper.readValue(response, DownstreamAgentListResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Downstream agent list failed: " + e.getMessage(), e);
        }
    }

    /**
     * Run an agent with the given request.
     * Calls POST /agents/{agentId}/run on the downstream service.
     *
     * @param request the run request containing agentId, query, userId, maxIterations, autoApprove
     * @return the run response from downstream
     */
    public DownstreamAgentRunResponse runAgent(DownstreamAgentRunRequest request) {
        WebClient client = WebClient.builder()
                .baseUrl(downstreamAgentUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();

        try {
            String requestUrl = downstreamAgentUrl + "/agents/" + request.getAgentId() + "/run";
            String requestBody = objectMapper.writeValueAsString(request);

            log.info("Calling downstream agent run - URL: {}", requestUrl);
            log.info("Downstream request body: {}", requestBody);

            String response = client.post()
                    .uri("/agents/" + request.getAgentId() + "/run")
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response == null || response.isBlank()) {
                throw new IllegalStateException("Downstream returned empty response");
            }

            log.info("Downstream response body: {}", response);

            DownstreamAgentRunResponse parsed = objectMapper.readValue(response, DownstreamAgentRunResponse.class);
            log.info("Downstream agent run completed. Status: {}", parsed.getStatus());
            return parsed;

        } catch (Exception e) {
            throw new IllegalStateException("Downstream agent run failed: " + e.getMessage(), e);
        }
    }

    // ==================== Session Management ====================

    /**
     * Get or create a session for the given agent.
     * If no session exists, creates a new one.
     *
     * @param agentId the downstream agent ID
     * @return the active session ID
     */
    public String getOrCreateSession(String agentId) {
        return activeSessionCache.computeIfAbsent(agentId, k -> {
            String newSessionId = UUID.randomUUID().toString();
            log.info("Created new session for agent {}: {}", agentId, newSessionId);
            return newSessionId;
        });
    }

    /**
     * Get the active session for an agent.
     *
     * @param agentId the downstream agent ID
     * @return the active session ID, or null if no active session
     */
    public String getActiveSession(String agentId) {
        return activeSessionCache.get(agentId);
    }

    /**
     * Check if the given session ID matches the active session for the agent.
     *
     * @param agentId the downstream agent ID
     * @param sessionId the session ID to validate
     * @return true if session is valid (matches active or no active session exists)
     */
    public boolean isValidSession(String agentId, String sessionId) {
        String activeSession = activeSessionCache.get(agentId);
        if (activeSession == null) {
            // No active session, any session is valid (or we'll create one)
            return true;
        }
        return activeSession.equals(sessionId);
    }

    /**
     * Clear the active session for an agent.
     *
     * @param agentId the downstream agent ID
     */
    public void clearSession(String agentId) {
        String removed = activeSessionCache.remove(agentId);
        if (removed != null) {
            log.info("Cleared session for agent {}: {}", agentId, removed);
        }
    }

    /**
     * Check if agent has an active session.
     *
     * @param agentId the downstream agent ID
     * @return true if there's an active session
     */
    public boolean hasActiveSession(String agentId) {
        return activeSessionCache.containsKey(agentId);
    }
}
