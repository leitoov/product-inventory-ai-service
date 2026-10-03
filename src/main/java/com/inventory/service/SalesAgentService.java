package com.inventory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.controller.dto.SalesAgentResponseDto;
import com.inventory.domain.model.Product;
import com.inventory.domain.model.SalesAgentResult;
import com.inventory.domain.model.SalesSession;
import com.inventory.domain.port.out.ProductRepositoryPort;
import com.inventory.domain.port.out.QuoteGeneratorPort;
import com.inventory.domain.port.out.SalesAgentPort;
import com.inventory.domain.port.out.SalesSessionRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalesAgentService {

    private final SalesAgentPort salesAgentPort;
    private final ProductRepositoryPort productRepository;
    private final QuoteGeneratorPort quoteGeneratorPort;
    private final SalesSessionRepositoryPort sessionRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public SalesAgentResponseDto processSalesChat(String sessionId, String instruction) {
        SalesSession session = resolveSession(sessionId);
        session.addUserMessage(instruction);

        // 1. Cargar el catálogo actual (simplificado a todos los productos con stock > 0)
        List<Product> catalog = productRepository.findAll().stream()
                .filter(p -> p.stock() != null && p.stock() > 0)
                .toList();
        
        String catalogContext;
        try {
            catalogContext = objectMapper.writeValueAsString(catalog.stream()
                    .map(p -> new SimplifiedProduct(p.sku(), p.name(), p.price(), p.stock()))
                    .toList());
        } catch (JsonProcessingException e) {
            catalogContext = "[]";
        }

        // 2. Construir el contexto de la sesión (carrito + historial)
        String sessionContext;
        try {
            sessionContext = objectMapper.writeValueAsString(new SessionContextData(
                    session.getCart(), session.buildContextSummary()
            ));
        } catch (JsonProcessingException e) {
            sessionContext = "{}";
        }

        // 3. Procesar con el agente de IA
        SalesAgentResult result = salesAgentPort.processSalesInstruction(instruction, catalogContext, sessionContext);
        session.addAgentMessage(result.message());
        session.updateCart(result.cart());

        String pdfBase64 = null;

        // 4. Si el agente determina CHECKOUT, descontamos stock y generamos el PDF
        if (result.action() == SalesAgentResult.Action.CHECKOUT) {
            log.info("Generando checkout para la sesión {}", session.getSessionId());
            
            // Descontar stock
            for (SalesSession.CartItem item : session.getCart()) {
                productRepository.findBySku(item.sku()).ifPresent(product -> {
                    int newStock = Math.max(0, product.stock() - item.quantity());
                    productRepository.save(product.withUpdatedFields(
                            product.name(), product.description(), product.imageUrl(), product.price(), newStock
                    ));
                });
            }

            // Generar PDF
            byte[] pdfBytes = quoteGeneratorPort.generateQuotePdf(session);
            pdfBase64 = Base64.getEncoder().encodeToString(pdfBytes);

            // Limpiar la sesión ya que terminó la compra
            sessionRepository.deleteById(session.getSessionId());
        } else {
            // Guardar progreso
            sessionRepository.save(session);
        }

        return new SalesAgentResponseDto(
                session.getSessionId(),
                result.message(),
                result.cart(),
                result.action().name(),
                pdfBase64
        );
    }

    private SalesSession resolveSession(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionRepository.findById(sessionId)
                    .orElseGet(() -> new SalesSession(sessionId));
        }
        return new SalesSession(UUID.randomUUID().toString());
    }

    record SimplifiedProduct(String sku, String name, java.math.BigDecimal price, Integer stock) {}
    record SessionContextData(List<SalesSession.CartItem> cart, String history) {}
}
