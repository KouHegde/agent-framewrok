package com.agentframework.data.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "agents")
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Core Info
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "goal", columnDefinition = "TEXT")
    private String goal;

    // Lookup Key (for deduplication)
    @Column(name = "allowed_tools", nullable = false, unique = true)
    private String allowedTools;  // Sorted comma-separated tools

    // Full Spec (JSON)
    @Column(name = "agent_spec", columnDefinition = "TEXT")
    private String agentSpec;

    // Ownership
    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "tenant_id")
    private String tenantId;

    // RAG Configuration
    @Column(name = "rag_scope")
    private String ragScope;  // Comma-separated

    // Reasoning Configuration
    @Column(name = "reasoning_style")
    private String reasoningStyle = "direct";

    @Column(name = "temperature", precision = 3, scale = 2)
    private BigDecimal temperature = new BigDecimal("0.30");

    // Retriever Configuration
    @Column(name = "retriever_type")
    private String retrieverType = "simple";

    @Column(name = "retriever_k")
    private Integer retrieverK = 5;

    // Execution Configuration
    @Column(name = "execution_mode")
    private String executionMode = "static";

    @Column(name = "permissions")
    private String permissions = "read_only";  // Comma-separated

    // Python AgentBrain Response
    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "max_steps")
    private Integer maxSteps = 6;

    @Column(name = "brain_agent_id")
    private String brainAgentId;

    // Status
    @Column(name = "status")
    private String status = "active";

    @Column(name = "downstream_status")
    private String downstreamStatus;

    // Timestamps
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // MCP Servers relationship
    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AgentMcpServer> mcpServers = new ArrayList<>();

    public Agent() {
    }

    public Agent(String name, String description, String allowedTools) {
        this.name = name;
        this.description = description;
        this.allowedTools = allowedTools;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public String getAllowedTools() {
        return allowedTools;
    }

    public void setAllowedTools(String allowedTools) {
        this.allowedTools = allowedTools;
    }

    public String getAgentSpec() {
        return agentSpec;
    }

    public void setAgentSpec(String agentSpec) {
        this.agentSpec = agentSpec;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getRagScope() {
        return ragScope;
    }

    public void setRagScope(String ragScope) {
        this.ragScope = ragScope;
    }

    public String getReasoningStyle() {
        return reasoningStyle;
    }

    public void setReasoningStyle(String reasoningStyle) {
        this.reasoningStyle = reasoningStyle;
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public void setTemperature(BigDecimal temperature) {
        this.temperature = temperature;
    }

    public String getRetrieverType() {
        return retrieverType;
    }

    public void setRetrieverType(String retrieverType) {
        this.retrieverType = retrieverType;
    }

    public Integer getRetrieverK() {
        return retrieverK;
    }

    public void setRetrieverK(Integer retrieverK) {
        this.retrieverK = retrieverK;
    }

    public String getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(String executionMode) {
        this.executionMode = executionMode;
    }

    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public Integer getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(Integer maxSteps) {
        this.maxSteps = maxSteps;
    }

    public String getBrainAgentId() {
        return brainAgentId;
    }

    public void setBrainAgentId(String brainAgentId) {
        this.brainAgentId = brainAgentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDownstreamStatus() {
        return downstreamStatus;
    }

    public void setDownstreamStatus(String downstreamStatus) {
        this.downstreamStatus = downstreamStatus;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<AgentMcpServer> getMcpServers() {
        return mcpServers;
    }

    public void setMcpServers(List<AgentMcpServer> mcpServers) {
        this.mcpServers = mcpServers;
    }

    public void addMcpServer(AgentMcpServer mcpServer) {
        mcpServers.add(mcpServer);
        mcpServer.setAgent(this);
    }

    public void removeMcpServer(AgentMcpServer mcpServer) {
        mcpServers.remove(mcpServer);
        mcpServer.setAgent(null);
    }
}
