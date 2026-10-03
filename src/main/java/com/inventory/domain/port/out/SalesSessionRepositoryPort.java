package com.inventory.domain.port.out;

import com.inventory.domain.model.SalesSession;
import java.util.Optional;

public interface SalesSessionRepositoryPort {
    SalesSession save(SalesSession session);
    Optional<SalesSession> findById(String sessionId);
    void deleteById(String sessionId);
}
