package com.inventory.infrastructure.ai;

/** Thrown when an agent level exceeds its configured timeout. */
public class AgentTimeoutException extends AgentCallException {
    public AgentTimeoutException(String agentLevel, long timeoutMs) {
        super(agentLevel, "timed out after " + timeoutMs + "ms");
    }
}
