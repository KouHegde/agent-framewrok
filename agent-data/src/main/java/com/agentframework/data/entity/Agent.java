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

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "agent_spec", columnDefinition = "TEXT")
    private String agentSpec;  // JSON string of the full AgentSpec

    @Column(name = "user_id")
    private String userId;

    @Column(name = "user_config")
    private String userConfig;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AgentMcpServer> mcpServers = new ArrayList<>();

    public Agent() {
    }

    public Agent(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public Agent(String name, String description, String userId) {
        this.name = name;
        this.description = description;
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

    public String getAgentSpec() {
        return agentSpec;
    }

    public void setAgentSpec(String agentSpec) {
        this.agentSpec = agentSpec;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserConfig() {
        return userConfig;
    }

    public void setUserConfig(String userConfig) {
        this.userConfig = userConfig;
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
