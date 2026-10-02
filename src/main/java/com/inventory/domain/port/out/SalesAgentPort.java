package com.inventory.domain.port.out;

import com.inventory.domain.model.SalesAgentResult;

public interface SalesAgentPort {
    /**
     * interacts with the AI agent to handle the sales process.
     * 
     * @param instruction The user's input.
     * @param catalogContext A JSON string containing the available products and their stock.
     * @param sessionContext A JSON string containing the current cart and conversation history.
     * @return the result from the agent.
     */
    SalesAgentResult processSalesInstruction(String instruction, String catalogContext, String sessionContext);
}
