package com.agentframework.controller;

import com.agentframework.dto.AgentExecutionRequest;
import com.agentframework.dto.AgentExecutionResponse;
import com.agentframework.dto.AgentSpec;
import com.agentframework.dto.ErrorResponse;
import com.agentframework.dto.RunAgentRequest;
import com.agentframework.facade.AgentFacade;
import com.agentframework.service.AgentExecutorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentExecutionController {

    private final AgentExecutorService agentExecutorService;
    private final AgentFacade agentFacade;
    private final ObjectMapper objectMapper;

    /**
     * Run an agent by ID.
     */
    @PostMapping("/{id}/run")
    public ResponseEntity<?> runAgentById(
            @PathVariable("id") UUID agentId,
            @RequestBody(required = false) RunAgentRequest request) {

        var agentOpt = agentFacade.findAgentById(agentId);
        if (agentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var agent = agentOpt.get();
        log.info("Running agent: {} ({})", agent.name(), agentId);

        try {
            AgentSpec agentSpec = objectMapper.readValue(agent.agentSpec(), AgentSpec.class);

            AgentExecutionRequest execRequest = new AgentExecutionRequest();
            execRequest.setAgentSpec(agentSpec);

            String query = request != null && request.getQuery() != null ? request.getQuery() : agent.description();
            execRequest.setQuery(query);

            if (request != null && request.getInputs() != null) {
                execRequest.setInputs(request.getInputs());
            }

            AgentExecutionResponse response = agentExecutorService.executeAgent(execRequest);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to run agent: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(
                    new ErrorResponse("Failed to run agent", e.getMessage())
            );
        }
    }
}
