package com.alibaba.assistant.agent.management.config;

import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

public class ConsoleWebMvcConfigurer implements WebMvcConfigurer {

    private final ExperienceConsoleProperties properties;

    public ConsoleWebMvcConfigurer(ExperienceConsoleProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String basePath = properties.getBasePath();
        if (!basePath.endsWith("/")) {
            basePath += "/";
        }
        registry.addResourceHandler(basePath + "**")
                .addResourceLocations("classpath:/static/exp-console/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        String basePath = properties.getBasePath();
        registry.addRedirectViewController(basePath, basePath + "/");
        registry.addViewController(basePath + "/").setViewName("forward:" + basePath + "/index.html");
    }
}
