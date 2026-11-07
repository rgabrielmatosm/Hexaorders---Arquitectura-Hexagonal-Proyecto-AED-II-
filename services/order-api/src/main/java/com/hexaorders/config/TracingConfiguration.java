package com.hexaorders.config;

import com.hexaorders.adapter.in.web.TraceIdResponseFilter;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class TracingConfiguration {
    /** Runs after the HTTP observation filter, so the server span is already open. */
    @Bean
    FilterRegistrationBean<TraceIdResponseFilter> traceIdResponseFilter(ObjectProvider<Tracer> tracer) {
        FilterRegistrationBean<TraceIdResponseFilter> registration = new FilterRegistrationBean<>(new TraceIdResponseFilter(tracer));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        return registration;
    }
}
