package com.inventory.domain.model;

import java.util.List;

public record SalesAgentResult(
        String message,
        List<SalesSession.CartItem> cart,
        Action action
) {
    public enum Action {
        CONTINUE, CHECKOUT
    }
}
