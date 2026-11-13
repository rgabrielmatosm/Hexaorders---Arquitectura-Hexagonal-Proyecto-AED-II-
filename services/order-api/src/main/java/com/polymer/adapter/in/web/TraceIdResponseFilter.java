package com.polymer.adapter.in.web;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Returns the trace id of each API request in the X-Trace-Id header, so a user (or a test) can open
 * exactly that trace in Grafana → Tempo. It contains no user data.
 */
public final class TraceIdResponseFilter extends OncePerRequestFilter {
    private final ObjectProvider<Tracer> tracer;

    public TraceIdResponseFilter(ObjectProvider<Tracer> tracer) { this.tracer = tracer; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Tracer current = tracer.getIfAvailable();
        Span span = current == null ? null : current.currentSpan();
        if (span != null && request.getRequestURI().startsWith("/api/")) {
            response.setHeader("X-Trace-Id", span.context().traceId());
        }
        chain.doFilter(request, response);
    }
}
