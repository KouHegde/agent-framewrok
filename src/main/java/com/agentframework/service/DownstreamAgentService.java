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

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownstreamAgentService {

    private final ObjectMapper objectMapper;

    @Value("${downstream.agent.url:http://localhost:8082}")
    private String downstreamAgentUrl;

    /**
     * In-memory session cache: agentId -> sessionId
     * For single pod deployment. Consider Redis/DB for multi-pod.
     */
    private final ConcurrentHashMap<String, String> sessionCache = new ConcurrentHashMap<>();

    public DownstreamAgentCreateResponse createAgent(DownstreamAgentCreateRequest request) {
        WebClient client = WebClient.builder()
                .baseUrl(downstreamAgentUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();

        try {
            log.info("Creating downstream agent: {}", request.getName());
            log.debug("Downstream create payload: {}", objectMapper.writeValueAsString(request));
            String response = client.post()
                    .uri("/agents")
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
                .build();

        try {
            String response = client.get()
                    .uri("/agents/" + agentId)
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
     * Manages session caching automatically - uses cached sessionId if available,
     * or caches the returned sessionId for subsequent calls.
     *
     * @param request the run request containing agentId, query, userId, context
     * @return the run response from downstream
     */
    public DownstreamAgentRunResponse runAgent(DownstreamAgentRunRequest request) {
        WebClient client = WebClient.builder()
                .baseUrl(downstreamAgentUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();

        try {
            // Check if we have a cached session for this agent
            String cachedSessionId = sessionCache.get(request.getAgentId());

            // Use cached session if request doesn't provide one
            if (request.getSessionId() == null && cachedSessionId != null) {
                log.debug("Using cached sessionId for agent {}: {}", request.getAgentId(), cachedSessionId);
                request.setSessionId(cachedSessionId);
            }

            log.info("Running downstream agent: {}, sessionId: {}",
                    request.getAgentId(), request.getSessionId());
            log.debug("Downstream run payload: {}", objectMapper.writeValueAsString(request));

            String response = client.post()
                    .uri("/agents/run")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response == null || response.isBlank()) {
                throw new IllegalStateException("Downstream returned empty response");
            }

            DownstreamAgentRunResponse parsed = objectMapper.readValue(response, DownstreamAgentRunResponse.class);

            // Cache the session ID if returned
            if (parsed.getSessionId() != null && !parsed.getSessionId().isBlank()) {
                sessionCache.put(request.getAgentId(), parsed.getSessionId());
                log.debug("Cached sessionId for agent {}: {}", request.getAgentId(), parsed.getSessionId());
            }

            log.info("Downstream agent run completed. Status: {}, SessionId: {}",
                    parsed.getStatus(), parsed.getSessionId());
            return parsed;

        } catch (Exception e) {
            throw new IllegalStateException("Downstream agent run failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get cached session ID for an agent.
     *
     * @param agentId the downstream agent ID
     * @return the cached session ID, or null if not cached
     */
    public String getCachedSessionId(String agentId) {
        return sessionCache.get(agentId);
    }

    /**
     * Clear cached session for an agent.
     *
     * @param agentId the downstream agent ID
     */
    public void clearSession(String agentId) {
        sessionCache.remove(agentId);
        log.info("Cleared cached session for agent: {}", agentId);
    }
}
