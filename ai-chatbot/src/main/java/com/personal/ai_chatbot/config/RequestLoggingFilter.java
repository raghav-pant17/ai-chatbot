package com.personal.ai_chatbot.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!shouldLog(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        long startedAt = System.currentTimeMillis();
        String requestId = request.getMethod() + " " + request.getRequestURI();
        String queryString = request.getQueryString();

        LOGGER.info(
                "API request started method={} path={} query={} origin={} contentType={} remoteAddr={}",
                request.getMethod(),
                request.getRequestURI(),
                queryString == null ? "" : queryString,
                request.getHeader("Origin"),
                request.getContentType(),
                request.getRemoteAddr());

        try {
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            LOGGER.error("API request failed before response request={}", requestId, ex);
            throw ex;
        } finally {
            LOGGER.info(
                    "API response sent method={} path={} status={} durationMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    System.currentTimeMillis() - startedAt);
        }
    }

    private boolean shouldLog(HttpServletRequest request) {
        return true;
    }
}
