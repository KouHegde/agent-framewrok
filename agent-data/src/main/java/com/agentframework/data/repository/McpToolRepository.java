package com.agentframework.data.repository;

import com.agentframework.data.entity.McpToolEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface McpToolRepository extends JpaRepository<McpToolEntity, UUID> {
    Optional<McpToolEntity> findByName(String name);

    List<McpToolEntity> findByNameIn(List<String> names);
}
