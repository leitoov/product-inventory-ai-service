package com.inventory.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Binds the 'ai.agents' configuration block, which defines
 * the ordered list of agent levels used for escalation.
 */
@ConfigurationProperties(prefix = "ai.agents")
public record AgentsProperties(List<AgentLevelConfig> levels) {}
