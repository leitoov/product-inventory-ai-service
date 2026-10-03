package com.inventory.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.domain.model.SalesSession;
import com.inventory.domain.port.out.SalesSessionRepositoryPort;
import com.inventory.infrastructure.persistence.entity.SalesSessionEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class SalesSessionPersistenceAdapter implements SalesSessionRepositoryPort {

    private final SalesSessionJpaRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    public SalesSession save(SalesSession session) {
        SalesSessionEntity entity = new SalesSessionEntity();
        entity.setSessionId(session.getSessionId());

        try {
            String cartJson = objectMapper.writeValueAsString(session.getCart());
            entity.setCartJson(cartJson);

            String historyJson = objectMapper.writeValueAsString(session.getConversationHistory());
            // Comprimir el historial antes de guardarlo en base de datos para no saturarla
            String compressedHistory = compress(historyJson);
            entity.setHistoryJson(compressedHistory);

        } catch (JsonProcessingException e) {
            log.error("Error serializando SalesSession", e);
        }

        repository.save(entity);
        return session;
    }

    @Override
    public Optional<SalesSession> findById(String sessionId) {
        return repository.findById(sessionId).map(entity -> {
            SalesSession session = new SalesSession(entity.getSessionId());

            try {
                if (entity.getCartJson() != null && !entity.getCartJson().isBlank()) {
                    List<SalesSession.CartItem> cart = objectMapper.readValue(
                            entity.getCartJson(),
                            new TypeReference<List<SalesSession.CartItem>>() {}
                    );
                    session.updateCart(cart);
                }

                if (entity.getHistoryJson() != null && !entity.getHistoryJson().isBlank()) {
                    // Descomprimir el historial
                    String historyJson = decompress(entity.getHistoryJson());
                    List<String> history = objectMapper.readValue(
                            historyJson,
                            new TypeReference<List<String>>() {}
                    );
                    history.forEach(msg -> {
                        if (msg.startsWith("USER: ")) {
                            session.addUserMessage(msg.substring(6));
                        } else if (msg.startsWith("AGENT: ")) {
                            session.addAgentMessage(msg.substring(7));
                        }
                    });
                }
            } catch (JsonProcessingException e) {
                log.error("Error deserializando SalesSession desde DB", e);
            }
            return session;
        });
    }

    @Override
    public void deleteById(String sessionId) {
        repository.deleteById(sessionId);
    }

    // Métodos de compresión GZIP en Base64 para reducir el tamaño del historial en DB
    private String compress(String data) {
        if (data == null || data.length() == 0) {
            return data;
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(data.getBytes());
            gzip.close();
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            log.warn("Error compressing data, returning uncompressed", e);
            return data;
        }
    }

    private String decompress(String compressedData) {
        if (compressedData == null || compressedData.length() == 0) {
            return compressedData;
        }
        try {
            byte[] compressed = Base64.getDecoder().decode(compressedData);
            try (ByteArrayInputStream in = new ByteArrayInputStream(compressed);
                 GZIPInputStream gzip = new GZIPInputStream(in)) {
                return new String(gzip.readAllBytes());
            }
        } catch (IllegalArgumentException | IOException e) {
            // Si falla asume que no estaba comprimido (retrocompatibilidad)
            return compressedData;
        }
    }
}
