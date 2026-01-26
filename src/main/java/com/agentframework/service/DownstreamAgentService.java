package com.agentframework.service;

import com.agentframework.dto.DownstreamAgentCreateRequest;
import com.agentframework.dto.DownstreamAgentCreateResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

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
}
