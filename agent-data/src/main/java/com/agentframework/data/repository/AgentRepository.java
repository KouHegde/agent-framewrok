package com.agentframework.data.repository;

import com.agentframework.data.entity.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentRepository extends JpaRepository<Agent, UUID> {

    Optional<Agent> findByName(String name);

    Optional<Agent> findByNameAndUserId(String name, String userId);

    boolean existsByName(String name);

    boolean existsByNameAndUserId(String name, String userId);

    List<Agent> findByUserId(String userId);

    List<Agent> findAllByOrderByCreatedAtDesc();
}
