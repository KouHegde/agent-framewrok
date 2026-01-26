package com.agentframework.controller;

import com.agentframework.common.dto.AgentDto;
import com.agentframework.dto.AgentConfigResponse;
import com.agentframework.dto.AgentCreateResponse;
import com.agentframework.dto.AgentDeleteResponse;
import com.agentframework.dto.AgentDetailResponse;
import com.agentframework.dto.AgentListResponse;
import com.agentframework.dto.AgentRunExample;
import com.agentframework.dto.AgentSpec;
import com.agentframework.dto.AgentSummaryResponse;
import com.agentframework.dto.CreateAgentRequest;
import com.agentframework.dto.ErrorResponse;
import com.agentframework.facade.AgentFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentCrudController {

    private final AgentFacade agentFacade;
    private final ObjectMapper objectMapper;

    /**
     * Create a new agent (persisted to database).
     */
    @PostMapping
    public ResponseEntity<AgentCreateResponse> createAgent(@RequestBody CreateAgentRequest request) {
        log.info("Creating agent: {}", request.getName());
        var result = agentFacade.createAgent(request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.response());
    }

    /**
     * List all agents.
     */
    @GetMapping
    public ResponseEntity<AgentListResponse> listAgents() {
        var agents = agentFacade.listAgents();
        List<AgentSummaryResponse> summaries = agents.stream()
                .map(this::toSummary)
                .toList();
        return ResponseEntity.ok(new AgentListResponse(summaries.size(), summaries, null));
    }

    /**
     * Get agent by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getAgent(@PathVariable("id") UUID agentId) {
        return agentFacade.findAgentById(agentId)
                .map(agent -> ResponseEntity.ok(toDetail(agent)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get agent config by ID.
     */
    @GetMapping("/{id}/config")
    public ResponseEntity<?> getAgentConfig(@PathVariable("id") UUID agentId) {
        var agentOpt = agentFacade.findAgentById(agentId);
        if (agentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var agent = agentOpt.get();
        return ResponseEntity.ok(new AgentConfigResponse(
                agent.id().toString(),
                splitCsv(agent.ragScope()),
                agent.reasoningStyle(),
                agent.temperature(),
                agent.retrieverType(),
                agent.retrieverK(),
                agent.executionMode(),
                splitCsv(agent.permissions())
        ));
    }

    /**
     * Delete an agent by ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAgent(@PathVariable("id") UUID agentId) {
        try {
            agentFacade.deleteAgent(agentId);
            return ResponseEntity.ok(new AgentDeleteResponse("deleted", agentId.toString()));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    private AgentSummaryResponse toSummary(AgentDto agent) {
        return new AgentSummaryResponse(
                agent.id().toString(),
                agent.name(),
                agent.description() != null ? agent.description() : "",
                agent.mcpServerNames(),
                agent.createdAt().toString()
        );
    }

    private AgentDetailResponse toDetail(AgentDto agent) {
        String agentId = agent.id().toString();
        String runEndpoint = "/api/agents/" + agentId + "/run";

        return new AgentDetailResponse(
                agentId,
                agent.name(),
                agent.description() != null ? agent.description() : "",
                parseAgentSpec(agent.agentSpec()),
                agent.mcpServerNames(),
                agent.createdAt().toString(),
                agent.updatedAt().toString(),
                runEndpoint,
                new AgentRunExample("POST", runEndpoint, Map.of("query", "Your query here"))
        );
    }

    private AgentSpec parseAgentSpec(String agentSpecJson) {
        if (agentSpecJson == null || agentSpecJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(agentSpecJson, AgentSpec.class);
        } catch (Exception e) {
            log.warn("Failed to parse agentSpec JSON for response: {}", e.getMessage());
            return null;
        }
    }

    private List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
