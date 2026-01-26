package com.agentframework.data.repository;

import com.agentframework.data.entity.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentRepository extends JpaRepository<Agent, UUID> {

    /**
     * Find agent by allowed tools (exact match).
     * This is the primary lookup for deduplication.
     */
    Optional<Agent> findByAllowedTools(String allowedTools);

    /**
     * Check if agent exists with given tools.
     */
    boolean existsByAllowedTools(String allowedTools);

    /**
     * Find agent by name (may return multiple if names are not unique).
     */
    List<Agent> findByName(String name);

    /**
     * Find agents by creator.
     */
    List<Agent> findByCreatedBy(String createdBy);

    /**
     * Find agents by tenant.
     */
    List<Agent> findByTenantId(String tenantId);

    /**
     * Find all active agents.
     */
    List<Agent> findByStatus(String status);

    /**
     * Find all agents ordered by creation date.
     */
    List<Agent> findAllByOrderByCreatedAtDesc();
}
