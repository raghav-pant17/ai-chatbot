package com.personal.ai_chatbot.service.impl;

import com.personal.ai_chatbot.dto.ProductResponse;
import com.personal.ai_chatbot.service.ProductCatalogService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class StaticProductCatalogService implements ProductCatalogService {

    private static final List<ProductResponse> PRODUCTS = List.of(
            new ProductResponse("PROD-SHOES", "Running Shoes", "Fashion", new BigDecimal("2200.00")),
            new ProductResponse("PROD-BAG", "Travel Backpack", "Accessories", new BigDecimal("1800.00")),
            new ProductResponse("PROD-HEADPHONES", "Wireless Headphones", "Electronics", new BigDecimal("2400.00")),
            new ProductResponse("PROD-WATCH", "Smart Watch", "Electronics", new BigDecimal("4200.00")),
            new ProductResponse("PROD-KURTA", "Cotton Kurta", "Fashion", new BigDecimal("1400.00")),
            new ProductResponse("PROD-LAMP", "Desk Lamp", "Home", new BigDecimal("900.00")),
            new ProductResponse("PROD-BOTTLE", "Steel Water Bottle", "Home", new BigDecimal("650.00")),
            new ProductResponse("PROD-BOOK", "Notebook Set", "Stationery", new BigDecimal("350.00"))
    );

    @Override
    public List<ProductResponse> findProducts() {
        return PRODUCTS;
    }

    @Override
    public Optional<ProductResponse> findProduct(String productId) {
        return PRODUCTS.stream()
                .filter(product -> product.productId().equals(productId))
                .findFirst();
    }
}
