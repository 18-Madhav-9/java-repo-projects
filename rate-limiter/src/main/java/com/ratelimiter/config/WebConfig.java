package com.ratelimiter.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration:
 * <ul>
 *   <li>Redirects {@code /} to the Swagger UI page</li>
 *   <li>Serves custom static resources (dark-mode CSS) from classpath</li>
 * </ul>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Redirect the root URL to Swagger UI so hitting {@code http://localhost:8080/}
     * immediately shows the API documentation instead of a 404 Whitelabel page.
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/swagger-ui/index.html");
    }

    /**
     * Serve static resources from {@code classpath:/static/} so that
     * the custom CSS file at {@code /css/swagger-dark.css} is accessible.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/");
    }
}
