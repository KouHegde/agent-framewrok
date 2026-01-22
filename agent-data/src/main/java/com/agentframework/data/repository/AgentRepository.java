package com.agentframework.data.repository;

import com.agentframework.data.entity.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentRepository extends JpaRepository<Agent, UUID> {

    Optional<Agent> findByUserIdAndBotId(String userId, String botId);

    boolean existsByUserIdAndBotId(String userId, String botId);
}
