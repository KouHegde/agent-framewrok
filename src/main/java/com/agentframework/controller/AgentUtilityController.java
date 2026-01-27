package com.agentframework.controller;

import com.agentframework.dto.HealthResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api")
public class AgentUtilityController {

    @Autowired(required = false)
    private com.agentframework.data.facade.AgentDataFacade agentDataFacade;

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse(
                "UP",
                "agent-framework",
                agentDataFacade != null ? "connected" : "not_configured"
        ));
    }

    // NOTE: /tools endpoint moved to ToolController for full CRUD support
}
