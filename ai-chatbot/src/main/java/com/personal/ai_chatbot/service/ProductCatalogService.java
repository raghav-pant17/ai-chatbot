package com.personal.ai_chatbot.service;

import com.personal.ai_chatbot.dto.ProductResponse;

import java.util.List;
import java.util.Optional;

public interface ProductCatalogService {

    List<ProductResponse> findProducts();

    Optional<ProductResponse> findProduct(String productId);
}
