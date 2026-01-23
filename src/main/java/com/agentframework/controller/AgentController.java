package com.agentframework.controller;

import com.agentframework.common.dto.AgentDto;
import com.agentframework.common.dto.CreateAgentRequest;
import com.agentframework.common.dto.UpdateAgentRequest;
import com.agentframework.data.facade.AgentDataFacade;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentDataFacade agentDataFacade;

    public AgentController(AgentDataFacade agentDataFacade) {
        this.agentDataFacade = agentDataFacade;
    }

    @PostMapping
    public ResponseEntity<AgentDto> createAgent(@RequestBody CreateAgentRequest request) {
        AgentDto agent = agentDataFacade.getOrCreateAgent(
                request.userId(),
                request.botId(),
                request.userConfig(),
                request.mcpServerNames()
        );
        return ResponseEntity.ok(agent);
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
}
