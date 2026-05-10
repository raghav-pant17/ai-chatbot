package com.personal.ai_chatbot.controller;

import com.personal.ai_chatbot.dto.ProductResponse;
import com.personal.ai_chatbot.service.ProductCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductCatalogService productCatalogService;

    public ProductController(ProductCatalogService productCatalogService) {
        this.productCatalogService = productCatalogService;
    }

    @GetMapping
    public List<ProductResponse> findProducts() {
        return productCatalogService.findProducts();
    }
}
