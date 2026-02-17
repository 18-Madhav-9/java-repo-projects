package com.ratelimiter.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 metadata configuration for V3.
 */
@Configuration
public class OpenApiConfig {

        @Bean
        public OpenAPI rateLimiterOpenAPI() {
                return new OpenAPI()
                                .info(new Info()
                                                .title("⚡ Rate Limiter API")
                                                .version("3.0.0")
                                                .description("""
                                                                **Production-ready pluggable rate limiting framework** with per-endpoint limits.

                                                                ### V3 Features
                                                                - 🔀 **Pluggable algorithms**: Fixed Window, Sliding Window, Token Bucket
                                                                - 🆔 **Identity resolution**: IP, API Key, JWT
                                                                - 📊 **Response headers**: X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset
                                                                - 🧹 **Automatic cleanup**: Scheduled stale-entry eviction
                                                                - 📈 **Metrics**: Micrometer counters via `/actuator/metrics`
                                                                - 🔧 **Admin APIs**: Live stats, active keys, config view

                                                                ### Configured Limits
                                                                | Endpoint | Limit |
                                                                |---|---|
                                                                | `GET /api/user` | 100 req/min |
                                                                | `GET /api/product` | 150 req/min |
                                                                | `GET /api/test` | 5 req/min |
                                                                | `POST /api/order` | 10 req/min |
                                                                | `POST /api/user` | 15 req/min |
                                                                | `PUT /api/user` | 15 req/min |
                                                                | `DELETE /api/user` | 5 req/min |
                                                                | *Other endpoints* | 20 req/min (default) |
                                                                """)
                                                .contact(new Contact()
                                                                .name("Madhav")
                                                                .url("https://github.com/18-Madhav-9"))
                                                .license(new License()
                                                                .name("MIT License")
                                                                .url("https://opensource.org/licenses/MIT")))
                                .servers(List.of(
                                                new Server().url("http://localhost:8080")
                                                                .description("Local Development")));
        }
}
