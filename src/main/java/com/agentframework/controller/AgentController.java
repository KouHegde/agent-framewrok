package com.agentframework.controller;

import com.agentframework.common.dto.AgentDto;
import com.agentframework.data.facade.AgentDataFacade;
import com.agentframework.dto.AgentSpec;
import com.agentframework.dto.CreateAgentRequest;
import com.agentframework.service.MetaAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final MetaAgentService metaAgentService;
    private final AgentDataFacade agentDataFacade;

    /**
     * Create a new agent. MetaAgentService analyzes the description
     * to determine which MCP servers are needed.
     */
    @PostMapping
    public ResponseEntity<AgentResponse> createAgent(@RequestBody CreateAgentRequest request) {
        log.info("Creating agent: {} for user: {}", request.getName(), request.getUserId());

        // 1. MetaAgentService analyzes description and selects MCP servers
        AgentSpec agentSpec = metaAgentService.buildAgentSpec(request);
        log.debug("Built agent spec with tools: {}", agentSpec.getAllowedTools());

        // 2. Persist agent with selected MCP servers
        AgentDto agent = agentDataFacade.getOrCreateAgent(
                request.getUserId(),
                request.getName(),  // use name as botId
                request.getDescription(),  // store description as userConfig
                agentSpec.getAllowedTools()  // MCP servers from analysis
        );

        return ResponseEntity.ok(new AgentResponse(agent, agentSpec));
    }

    @GetMapping("/{userId}/{botId}")
    public ResponseEntity<AgentDto> getAgent(
            @PathVariable String userId,
            @PathVariable String botId) {
        return agentDataFacade.findAgent(userId, botId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/id/{agentId}")
    public ResponseEntity<AgentDto> getAgentById(@PathVariable UUID agentId) {
        return agentDataFacade.findAgentById(agentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{agentId}")
    public ResponseEntity<AgentDto> updateAgent(
            @PathVariable UUID agentId,
            @RequestBody UpdateAgentRequest request) {
        AgentDto updated = agentDataFacade.updateAgent(
                agentId,
                request.userConfig(),
                request.mcpServerNames()
        );
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{agentId}")
    public ResponseEntity<Void> deleteAgent(@PathVariable UUID agentId) {
        agentDataFacade.deleteAgent(agentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Response combining persisted agent data with the generated spec.
     */
    public record AgentResponse(AgentDto agent, AgentSpec spec) {}

    /**
     * Request for updating an existing agent.
     */
    public record UpdateAgentRequest(String userConfig, List<String> mcpServerNames) {}
}
