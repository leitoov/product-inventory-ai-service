package com.inventory.infrastructure.ai;

/** Thrown when an agent level HTTP call fails (non-timeout reason). */
public class AgentCallException extends RuntimeException {
    private final String agentLevel;

    public AgentCallException(String agentLevel, String message) {
        super("[" + agentLevel + "] " + message);
        this.agentLevel = agentLevel;
    }

    public String getAgentLevel() { return agentLevel; }
}
