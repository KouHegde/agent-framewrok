package com.agentframework.data.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Configuration for the agent-data module.
 * Enables JPA repositories and entity scanning for this module.
 */
@Configuration
@EntityScan(basePackages = "com.agentframework.data.entity")
@EnableJpaRepositories(basePackages = "com.agentframework.data.repository")
public class DataModuleConfig {
}
