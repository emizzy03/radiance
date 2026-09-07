package com.example.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the admin and storefront single-page apps when the browser requests
 * the {@code /admin} or {@code /shop} directory (with or without a trailing
 * slash).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/admin").setViewName("redirect:/admin/");
        registry.addViewController("/admin/").setViewName("forward:/admin/index.html");
        registry.addViewController("/shop").setViewName("redirect:/shop/");
        registry.addViewController("/shop/").setViewName("forward:/shop/index.html");
    }
}
