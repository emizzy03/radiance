package com.example.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the admin single-page app when the browser requests the {@code /admin}
 * directory (with or without a trailing slash).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/admin").setViewName("redirect:/admin/");
        registry.addViewController("/admin/").setViewName("forward:/admin/index.html");
    }
}
