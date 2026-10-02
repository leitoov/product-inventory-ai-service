package com.inventory.domain.port.out;

import com.inventory.domain.model.SalesSession;

public interface QuoteGeneratorPort {
    /**
     * Generates a PDF quote for the given sales session.
     * 
     * @param session The sales session containing the final cart.
     * @return The bytes of the generated PDF.
     */
    byte[] generateQuotePdf(SalesSession session);
}
