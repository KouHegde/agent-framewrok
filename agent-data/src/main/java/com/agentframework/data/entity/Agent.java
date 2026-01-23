package com.agentframework.data.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

    @Column(name = "bot_id", nullable = false)
    private String botId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    // Agent description/goal
    @Column(name = "description")
    private String description;

    @Column(name = "goal")
    private String goal;

    // RAG Configuration
    @Column(name = "rag_scope", columnDefinition = "TEXT[]")
    @Convert(converter = StringArrayConverter.class)
    private List<String> ragScope = new ArrayList<>();

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

    @Column(name = "permissions", columnDefinition = "TEXT[]")
    @Convert(converter = StringArrayConverter.class)
    private List<String> permissions = new ArrayList<>(List.of("read_only"));

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

    public Agent(String botId, String userId) {
        this.botId = botId;
        this.userId = userId;
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

    public String getBotId() {
        return botId;
    }

    public void setBotId(String botId) {
        this.botId = botId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

    public List<String> getRagScope() {
        return ragScope;
    }

    public void setRagScope(List<String> ragScope) {
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

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
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
