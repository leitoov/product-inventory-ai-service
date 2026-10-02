package com.inventory.domain.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class SalesSession {
    private final String sessionId;
    private final List<CartItem> cart;
    private final List<String> conversationHistory;

    public SalesSession(String sessionId) {
        this.sessionId = sessionId;
        this.cart = new ArrayList<>();
        this.conversationHistory = new ArrayList<>();
    }

    public String getSessionId() {
        return sessionId;
    }

    public List<CartItem> getCart() {
        return cart;
    }

    public void updateCart(List<CartItem> newCart) {
        this.cart.clear();
        if (newCart != null) {
            this.cart.addAll(newCart);
        }
    }

    public List<String> getConversationHistory() {
        return conversationHistory;
    }

    public void addUserMessage(String message) {
        conversationHistory.add("USER: " + message);
    }

    public void addAgentMessage(String message) {
        conversationHistory.add("AGENT: " + message);
    }

    public String buildContextSummary() {
        if (conversationHistory.isEmpty()) return "No previous conversation.";
        int start = Math.max(0, conversationHistory.size() - 10); // Keep last 10 turns
        return String.join("\n", conversationHistory.subList(start, conversationHistory.size()));
    }

    public BigDecimal getTotal() {
        return cart.stream()
                .map(item -> item.price().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record CartItem(String sku, String name, int quantity, BigDecimal price) {}
}
