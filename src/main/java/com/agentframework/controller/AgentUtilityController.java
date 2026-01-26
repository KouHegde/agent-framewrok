package com.agentframework.controller;

import com.agentframework.dto.HealthResponse;
import com.agentframework.dto.ToolCategoryResponse;
import com.agentframework.dto.ToolSummaryResponse;
import com.agentframework.dto.ToolsResponse;
import com.agentframework.registry.MCPToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AgentUtilityController {

    private final MCPToolRegistry toolRegistry;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
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

    /**
     * Get all available MCP tools
     */
    @GetMapping("/tools")
    public ResponseEntity<ToolsResponse> getTools() {
        var tools = toolRegistry.getAllTools();
        var byCategory = toolRegistry.getToolsByCategories();

        List<ToolCategoryResponse> categories = byCategory.entrySet().stream()
                .map(entry -> new ToolCategoryResponse(
                        entry.getKey(),
                        entry.getValue().stream()
                                .map(t -> new ToolSummaryResponse(
                                        t.getName(),
                                        t.getDescription(),
                                        t.getCapabilities()
                                ))
                                .toList()
                ))
                .toList();

        return ResponseEntity.ok(new ToolsResponse(tools.size(), categories));
    }
}
