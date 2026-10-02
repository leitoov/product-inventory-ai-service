package com.inventory.domain.port.out;

/**
 * Output port — AI analytics abstraction.
 *
 * Implementations live in infrastructure/adapter/out/ai/.
 * The domain never knows which AI provider is active.
 *
 * To add a new provider: implement this interface and activate
 * the corresponding Spring profile.
 */
public interface AiAnalyticsPort {

    /**
     * Generates a natural-language summary of the provided sales data.
     *
     * @param salesDataJson  JSON-serialized sales metrics to be summarized.
     * @return               Human-readable summary text.
     */
    String generateSalesSummary(String salesDataJson);

    /**
     * Generates actionable insights for the given product list.
     *
     * @param productsJson  JSON-serialized list of products.
     * @return              List of insight strings.
     */
    java.util.List<String> generateProductInsights(String productsJson);
}
