package com.ratelimiter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * V2 test controller — provides endpoints matching the per-API rate limit config
 * so the rate limiter can be demonstrated end-to-end.
 */
@RestController
@RequestMapping("/api")
public class TestController {

    // ============================================================
    // User Endpoints
    // ============================================================

    @Tag(name = "User", description = "User CRUD operations")
    @Operation(summary = "Get user", description = "Retrieve user data. **Rate limit: 100 req/min**")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User retrieved successfully"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"error\": \"Too many requests. Please try again later.\"}")))
    })
    @GetMapping("/user")
    public ResponseEntity<Map<String, String>> getUser() {
        return ResponseEntity.ok(Map.of("message", "GET /api/user — successful", "limit", "100/min"));
    }

    @Tag(name = "User")
    @Operation(summary = "Create user", description = "Register a new user. **Rate limit: 15 req/min**")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User created successfully"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"error\": \"Too many requests. Please try again later.\"}")))
    })
    @PostMapping("/user")
    public ResponseEntity<Map<String, String>> createUser() {
        return ResponseEntity.ok(Map.of("message", "POST /api/user — successful", "limit", "15/min"));
    }

    @Tag(name = "User")
    @Operation(summary = "Update user", description = "Update existing user data. **Rate limit: 15 req/min**")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User updated successfully"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"error\": \"Too many requests. Please try again later.\"}")))
    })
    @PutMapping("/user")
    public ResponseEntity<Map<String, String>> updateUser() {
        return ResponseEntity.ok(Map.of("message", "PUT /api/user — successful", "limit", "15/min"));
    }

    @Tag(name = "User")
    @Operation(summary = "Delete user", description = "Delete a user account. **Rate limit: 5 req/min** ⚠️ Strict")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User deleted successfully"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"error\": \"Too many requests. Please try again later.\"}")))
    })
    @DeleteMapping("/user")
    public ResponseEntity<Map<String, String>> deleteUser() {
        return ResponseEntity.ok(Map.of("message", "DELETE /api/user — successful", "limit", "5/min"));
    }

    // ============================================================
    // Product Endpoints
    // ============================================================

    @Tag(name = "Product", description = "Product catalog operations")
    @Operation(summary = "Get product", description = "Retrieve product catalog data. **Rate limit: 150 req/min**")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product retrieved successfully"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"error\": \"Too many requests. Please try again later.\"}")))
    })
    @GetMapping("/product")
    public ResponseEntity<Map<String, String>> getProduct() {
        return ResponseEntity.ok(Map.of("message", "GET /api/product — successful", "limit", "150/min"));
    }

    // ============================================================
    // Order Endpoints
    // ============================================================

    @Tag(name = "Order", description = "Order management operations")
    @Operation(summary = "Create order", description = "Place a new order. **Rate limit: 10 req/min** — Expensive operation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order placed successfully"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"error\": \"Too many requests. Please try again later.\"}")))
    })
    @PostMapping("/order")
    public ResponseEntity<Map<String, String>> createOrder() {
        return ResponseEntity.ok(Map.of("message", "POST /api/order — successful", "limit", "10/min"));
    }

    // ============================================================
    // Test Endpoint
    // ============================================================

    @Tag(name = "Test", description = "Health check & rate limiter testing")
    @Operation(summary = "Test endpoint", description = "Simple test endpoint to verify the rate limiter. **Rate limit: 5 req/min** — Very strict")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Request successful"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"error\": \"Too many requests. Please try again later.\"}")))
    })
    @GetMapping("/test")
    public ResponseEntity<Map<String, String>> test() {
        return ResponseEntity.ok(Map.of("message", "GET /api/test — successful", "limit", "5/min"));
    }
}
