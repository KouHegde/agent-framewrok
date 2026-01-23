package com.agentframework.controller;

import com.agentframework.common.dto.AgentConfigDto;
import com.agentframework.common.dto.AgentDto;
import com.agentframework.dto.CreateAgentRequest;
import com.agentframework.facade.AgentFacade;
import com.agentframework.facade.AgentFacade.AgentCreationResult;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentFacade agentFacade;

    @PostMapping
    public ResponseEntity<AgentCreationResult> createAgent(@RequestBody CreateAgentRequest request) {
        return ResponseEntity.ok(
                agentFacade.createAgent(request.getUserId(), request.getName(), request.getDescription())
        );
    }

    @PostMapping("/{agentId}/refresh-config")
    public ResponseEntity<AgentDto> refreshConfig(@PathVariable UUID agentId) {
        return ResponseEntity.ok(agentFacade.refreshAgentConfig(agentId));
    }

    @PutMapping("/{agentId}/config")
    public ResponseEntity<AgentDto> updateConfig(
            @PathVariable UUID agentId,
            @RequestBody AgentConfigDto config) {
        return ResponseEntity.ok(agentFacade.updateAgentConfig(agentId, config));
    }

    @PutMapping("/{agentId}/mcp-servers")
    public ResponseEntity<AgentDto> updateMcpServers(
            @PathVariable UUID agentId,
            @RequestBody List<String> mcpServerNames) {
        return ResponseEntity.ok(agentFacade.updateAgentMcpServers(agentId, mcpServerNames));
    }

    @GetMapping("/{userId}/{botId}")
    public ResponseEntity<AgentDto> getAgent(
            @PathVariable String userId,
            @PathVariable String botId) {
        return agentFacade.findAgent(userId, botId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/id/{agentId}")
    public ResponseEntity<AgentDto> getAgentById(@PathVariable UUID agentId) {
        return agentFacade.findAgentById(agentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{agentId}")
    public ResponseEntity<Void> deleteAgent(@PathVariable UUID agentId) {
        agentFacade.deleteAgent(agentId);
        return ResponseEntity.noContent().build();
    }
}
