package com.agentframework.controller;

import com.agentframework.dto.ErrorResponse;
import com.agentframework.dto.ToolRequest;
import com.agentframework.dto.ToolResponse;
import com.agentframework.registry.MCPTool;
import com.agentframework.registry.MCPToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class ToolController {

    private final MCPToolRegistry toolRegistry;

    /**
     * List all tools, optionally filtered by category.
     */
    @GetMapping
    public ResponseEntity<List<ToolResponse>> listTools(
            @RequestParam(required = false) String category) {

        List<MCPTool> tools;
        if (category != null && !category.isBlank()) {
            tools = toolRegistry.getToolsByCategory(category);
        } else {
            tools = toolRegistry.getAllTools();
        }

        List<ToolResponse> response = tools.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get tools grouped by category.
     */
    @GetMapping("/categories")
    public ResponseEntity<Map<String, List<ToolResponse>>> getToolsByCategories() {
        Map<String, List<MCPTool>> toolsByCategory = toolRegistry.getToolsByCategories();

        Map<String, List<ToolResponse>> response = toolsByCategory.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream().map(this::toResponse).collect(Collectors.toList())
                ));

        return ResponseEntity.ok(response);
    }

    /**
     * Get a specific tool by name.
     */
    @GetMapping("/{name}")
    public ResponseEntity<?> getTool(@PathVariable String name) {
        return toolRegistry.getTool(name)
                .map(tool -> ResponseEntity.ok(toResponse(tool)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Add a new tool to the registry.
     */
    @PostMapping
    public ResponseEntity<?> addTool(@RequestBody ToolRequest request) {
        // Validate required fields
        if (request.getName() == null || request.getName().isBlank()) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("Validation error", "Tool name is required")
            );
        }

        if (request.getCategory() == null || request.getCategory().isBlank()) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("Validation error", "Tool category is required")
            );
        }

        // Check if tool already exists
        if (toolRegistry.exists(request.getName())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    new ErrorResponse("Tool already exists", "Tool with name '" + request.getName() + "' already exists")
            );
        }

        try {
            MCPTool tool = MCPTool.builder()
                    .name(request.getName())
                    .description(request.getDescription())
                    .category(request.getCategory())
                    .capabilities(request.getCapabilities() != null ? request.getCapabilities() : List.of())
                    .requiredInputs(request.getRequiredInputs() != null ? request.getRequiredInputs() : List.of())
                    .build();

            MCPTool created = toolRegistry.addTool(tool);
            log.info("Created tool: {}", created.getName());

            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));

        } catch (Exception e) {
            log.error("Failed to create tool: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(
                    new ErrorResponse("Failed to create tool", e.getMessage())
            );
        }
    }

    /**
     * Update an existing tool.
     */
    @PutMapping("/{name}")
    public ResponseEntity<?> updateTool(
            @PathVariable String name,
            @RequestBody ToolRequest request) {

        if (!toolRegistry.exists(name)) {
            return ResponseEntity.notFound().build();
        }

        try {
            MCPTool updatedTool = MCPTool.builder()
                    .name(request.getName() != null ? request.getName() : name)
                    .description(request.getDescription())
                    .category(request.getCategory())
                    .capabilities(request.getCapabilities() != null ? request.getCapabilities() : List.of())
                    .requiredInputs(request.getRequiredInputs() != null ? request.getRequiredInputs() : List.of())
                    .build();

            MCPTool updated = toolRegistry.updateTool(name, updatedTool);
            log.info("Updated tool: {} -> {}", name, updated.getName());

            return ResponseEntity.ok(toResponse(updated));

        } catch (Exception e) {
            log.error("Failed to update tool: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(
                    new ErrorResponse("Failed to update tool", e.getMessage())
            );
        }
    }

    /**
     * Delete a tool from the registry.
     */
    @DeleteMapping("/{name}")
    public ResponseEntity<?> deleteTool(@PathVariable String name) {
        if (!toolRegistry.exists(name)) {
            return ResponseEntity.notFound().build();
        }

        try {
            boolean deleted = toolRegistry.deleteTool(name);
            if (deleted) {
                log.info("Deleted tool: {}", name);
                return ResponseEntity.ok(Map.of(
                        "message", "Tool deleted successfully",
                        "name", name
                ));
            } else {
                return ResponseEntity.internalServerError().body(
                        new ErrorResponse("Failed to delete tool", "Unknown error")
                );
            }

        } catch (Exception e) {
            log.error("Failed to delete tool: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(
                    new ErrorResponse("Failed to delete tool", e.getMessage())
            );
        }
    }

    /**
     * Bulk add tools from a list.
     */
    @PostMapping("/bulk")
    public ResponseEntity<?> bulkAddTools(@RequestBody List<ToolRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("Validation error", "Tool list is required")
            );
        }

        int added = 0;
        int skipped = 0;

        for (ToolRequest request : requests) {
            if (request.getName() == null || request.getName().isBlank()) {
                skipped++;
                continue;
            }

            if (toolRegistry.exists(request.getName())) {
                skipped++;
                continue;
            }

            try {
                MCPTool tool = MCPTool.builder()
                        .name(request.getName())
                        .description(request.getDescription())
                        .category(request.getCategory() != null ? request.getCategory() : "unknown")
                        .capabilities(request.getCapabilities() != null ? request.getCapabilities() : List.of())
                        .requiredInputs(request.getRequiredInputs() != null ? request.getRequiredInputs() : List.of())
                        .build();

                toolRegistry.addTool(tool);
                added++;
            } catch (Exception e) {
                log.warn("Failed to add tool {}: {}", request.getName(), e.getMessage());
                skipped++;
            }
        }

        log.info("Bulk add tools: {} added, {} skipped", added, skipped);
        return ResponseEntity.ok(Map.of(
                "added", added,
                "skipped", skipped,
                "total", requests.size()
        ));
    }

    private ToolResponse toResponse(MCPTool tool) {
        return ToolResponse.builder()
                .name(tool.getName())
                .description(tool.getDescription())
                .category(tool.getCategory())
                .capabilities(tool.getCapabilities())
                .requiredInputs(tool.getRequiredInputs())
                .build();
    }
}
