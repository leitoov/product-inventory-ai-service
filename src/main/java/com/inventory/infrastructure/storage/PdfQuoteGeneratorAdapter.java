package com.inventory.infrastructure.storage;

import com.inventory.domain.model.SalesSession;
import com.inventory.domain.port.out.QuoteGeneratorPort;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Slf4j
@Component
public class PdfQuoteGeneratorAdapter implements QuoteGeneratorPort {

    @Override
    public byte[] generateQuotePdf(SalesSession session) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText("Cotizacion Comercial - Orden de Compra");
                contentStream.endText();

                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 660);
                contentStream.setLeading(14.5f);
                
                contentStream.showText("ID Sesion: " + session.getSessionId());
                contentStream.newLine();
                contentStream.newLine();
                
                contentStream.showText("Detalles del Pedido:");
                contentStream.newLine();
                
                for (SalesSession.CartItem item : session.getCart()) {
                    contentStream.showText(String.format("- %s (%s): %d x $%.2f", 
                            item.name(), item.sku(), item.quantity(), item.price()));
                    contentStream.newLine();
                }
                
                contentStream.newLine();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                contentStream.showText(String.format("TOTAL: $%.2f", session.getTotal()));
                
                contentStream.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Error al generar PDF de cotización: {}", e.getMessage());
            throw new RuntimeException("Error generando cotización en PDF", e);
        }
    }
}
