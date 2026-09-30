package org.example.orderservice.models.services.impl;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.example.orderservice.clients.ProductClient;
import org.example.orderservice.exceptions.ProductNotFoundException;
import org.example.orderservice.exceptions.ProductServiceException;
import org.example.orderservice.models.dto.responses.ProductResponse;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductGatewayService {

    private final ProductClient productClient;

    @CircuitBreaker(name = "productService", fallbackMethod = "getProductByIdFallback")
    public ProductResponse getProductById(Long productId) {
        ProductResponse product = productClient.getProductById(productId);
        if (product == null || product.id() == null) {
            throw new ProductNotFoundException(productId);
        }
        return product;
    }

    private ProductResponse getProductByIdFallback(Long productId, Throwable throwable) {
        if (throwable instanceof FeignException feignException) {
            if (feignException.status() == 404) {
                throw new ProductNotFoundException(productId);
            }
        }
        throw new ProductServiceException("Product service is unavailable", throwable);
    }
}
