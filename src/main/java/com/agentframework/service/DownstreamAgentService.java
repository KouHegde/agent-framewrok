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

@Slf4j
@Service
@RequiredArgsConstructor
public class DownstreamAgentService {

    private final ObjectMapper objectMapper;

    @Value("${downstream.agent.url:http://localhost:8082}")
    private String downstreamAgentUrl;

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
     * Calls POST /agents/{agentId}/run on the downstream service.
     *
     * @param request the run request containing agentId, query, userId, maxIterations, autoApprove
     * @return the run response from downstream
     */
    public DownstreamAgentRunResponse runAgent(DownstreamAgentRunRequest request) {
        WebClient client = WebClient.builder()
                .baseUrl(downstreamAgentUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();

        try {
            log.info("Running downstream agent: {}", request.getAgentId());
            log.debug("Downstream run payload: {}", objectMapper.writeValueAsString(request));

            String response = client.post()
                    .uri("/agents/" + request.getAgentId() + "/run")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response == null || response.isBlank()) {
                throw new IllegalStateException("Downstream returned empty response");
            }

            DownstreamAgentRunResponse parsed = objectMapper.readValue(response, DownstreamAgentRunResponse.class);
            log.info("Downstream agent run completed. Status: {}", parsed.getStatus());
            return parsed;

        } catch (Exception e) {
            throw new IllegalStateException("Downstream agent run failed: " + e.getMessage(), e);
        }
    }
}
