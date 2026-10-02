package com.inventory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.domain.model.Product;
import com.inventory.domain.port.in.SalesMetricsPort;
import com.inventory.domain.port.out.AiAnalyticsPort;
import com.inventory.domain.port.out.ProductRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalesMetricsService implements SalesMetricsPort {

    private final ProductRepositoryPort productRepository;
    private final AiAnalyticsPort aiAnalyticsPort;
    private final ObjectMapper objectMapper;

    @Override
    public MetricsResult getMetrics(String startDate, String endDate, String category, String promptAgent) {
        log.info("Obteniendo métricas con filtros: inicio={}, fin={}, categoría={}", startDate, endDate, category);

        // Al no tener aún entidad de 'Ventas', calculamos métricas basadas en el inventario actual
        // como un placeholder para el dashboard.
        List<Product> allProducts = productRepository.findAll(0, 1000).products(); // Simulamos traer todo

        long totalProducts = allProducts.size();
        long totalStock = allProducts.stream().mapToLong(p -> p.stock() != null ? p.stock() : 0).sum();
        
        BigDecimal totalInventoryValue = allProducts.stream()
                .map(p -> {
                    BigDecimal price = p.price() != null ? p.price() : BigDecimal.ZERO;
                    long stock = p.stock() != null ? p.stock() : 0;
                    return price.multiply(BigDecimal.valueOf(stock));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Simulamos algunos datos de ventas
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalProductos", totalProducts);
        metrics.put("stockTotal", totalStock);
        metrics.put("valorInventarioEstimado", totalInventoryValue);
        metrics.put("ventasMesPasado", 1520); // mock
        metrics.put("ingresosMesPasado", new BigDecimal("45990.50")); // mock
        
        if (startDate != null) metrics.put("filtro_inicio", startDate);
        if (endDate != null) metrics.put("filtro_fin", endDate);
        if (category != null) metrics.put("filtro_categoria", category);

        String aiInsights = null;

        // Si el usuario envió un prompt o solicitó insights (promptAgent puede ser vacío para un resumen general)
        if (promptAgent != null) {
            try {
                String jsonMetrics = objectMapper.writeValueAsString(metrics);
                aiInsights = aiAnalyticsPort.analyzeMetrics(jsonMetrics, promptAgent);
            } catch (Exception e) {
                log.error("No se pudo serializar las métricas o conectar con IA: {}", e.getMessage());
                aiInsights = "No se pudieron generar insights en este momento.";
            }
        }

        return new MetricsResult(metrics, aiInsights);
    }
}
