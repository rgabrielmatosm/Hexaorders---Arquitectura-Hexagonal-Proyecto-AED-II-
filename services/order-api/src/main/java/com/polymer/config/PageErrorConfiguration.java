package com.polymer.config;

import org.springframework.boot.autoconfigure.web.servlet.error.ErrorViewResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.ModelAndView;

/** Browsers asking for an unknown page get the designed /404.html (status 404); APIs keep JSON errors. */
@Configuration(proxyBeanMethods = false)
public class PageErrorConfiguration {
    @Bean
    ErrorViewResolver pageErrorViews() {
        return (request, status, model) -> switch (status) {
            case NOT_FOUND -> new ModelAndView("forward:/404.html", HttpStatus.NOT_FOUND);
            case FORBIDDEN -> new ModelAndView("forward:/403.html", HttpStatus.FORBIDDEN);
            default -> null;
        };
    }
}
