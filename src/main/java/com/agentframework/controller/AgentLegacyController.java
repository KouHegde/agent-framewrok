package com.agentframework.controller;

import com.agentframework.dto.AgentExecutionRequest;
import com.agentframework.dto.AgentExecutionResponse;
import com.agentframework.dto.AgentSpec;
import com.agentframework.dto.CreateAgentRequest;
import com.agentframework.dto.CreateAndRunRequest;
import com.agentframework.service.AgentExecutorService;
import com.agentframework.service.MetaAgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AgentLegacyController {

    private final MetaAgentService metaAgentService;
    private final AgentExecutorService agentExecutorService;

    /**
     * LEGACY: Create an agent specification from name and description (not persisted).
     */
    @PostMapping("/create-agent")
    public ResponseEntity<AgentSpec> createAgentSpec(@Valid @RequestBody CreateAgentRequest request) {
        log.info("Received create-agent request for: {}", request.getName());

        AgentSpec agentSpec = metaAgentService.buildAgentSpec(request);

        log.info("Generated agent spec: {} with tools: {}",
                agentSpec.getAgentName(),
                agentSpec.getAllowedTools());

        return ResponseEntity.ok(agentSpec);
    }

    /**
     * Run an agent with a given spec and query.
     */
    @PostMapping("/run-agent")
    public ResponseEntity<AgentExecutionResponse> runAgent(@Valid @RequestBody AgentExecutionRequest request) {
        log.info("Received run-agent request for: {}", request.getAgentSpec().getAgentName());
        log.info("Query: {}", request.getQuery());

        AgentExecutionResponse response = agentExecutorService.executeAgent(request);

        log.info("Agent execution completed. Status: {}, Tools used: {}",
                response.getStatus(),
                response.getToolsUsed().size());

        return ResponseEntity.ok(response);
    }

    /**
     * Combined endpoint: Create agent spec and run it in one call.
     */
    @PostMapping("/create-and-run-agent")
    public ResponseEntity<AgentExecutionResponse> createAndRunAgent(
            @RequestBody CreateAndRunRequest request) {

        log.info("Received create-and-run-agent request for: {}", request.getName());

        CreateAgentRequest createRequest = new CreateAgentRequest();
        createRequest.setName(request.getName());
        createRequest.setDescription(request.getDescription());
        AgentSpec agentSpec = metaAgentService.buildAgentSpec(createRequest);

        AgentExecutionRequest execRequest = new AgentExecutionRequest();
        execRequest.setAgentSpec(agentSpec);
        String query = request.getQuery();
        execRequest.setQuery(query != null && !query.isBlank() ? query : request.getDescription());
        execRequest.setInputs(request.getInputs());

        AgentExecutionResponse response = agentExecutorService.executeAgent(execRequest);

        return ResponseEntity.ok(response);
    }
}
