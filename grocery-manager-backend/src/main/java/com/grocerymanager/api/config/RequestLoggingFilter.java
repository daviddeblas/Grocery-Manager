package com.grocerymanager.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Enumeration;

/**
 * This class is a Spring filter that logs all incoming HTTP requests and outgoing HTTP responses.
 * It captures:
 * - HTTP method, URI, and execution time.
 * - Request and response headers (Authorization token is anonymized).
 * - Request and response body (truncated if too long).
 * - Response status code.
 */
@Component
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    /** Logger for API request logging */
    private static final Logger logger = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private static final int MAX_PAYLOAD_LENGTH = 10000;


    /**
     * Intercepts HTTP requests and responses to log relevant information.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Wrap the request and response to capture the payload
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();
        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            long duration = System.currentTimeMillis() - startTime; // Calculate execution time
            String requestBody = getStringValue(requestWrapper.getContentAsByteArray(), request.getCharacterEncoding());
            String responseBody = getStringValue(responseWrapper.getContentAsByteArray(), response.getCharacterEncoding());

            logger.info("\n-------- API REQUEST --------\n" +
                            "Method: {} | URI: {} | Duration: {}ms\n" +
                            "Headers: {}\n" +
                            "Request Body: {}\n" +
                            "Response Status: {}\n" +
                            "Response Headers: {}\n" +
                            "Response Body: {}\n" +
                            "-------- END REQUEST --------",
                    request.getMethod(), request.getRequestURI(), duration,
                    getHeaders(request),
                    truncateString(requestBody),
                    response.getStatus(),
                    getResponseHeaders(responseWrapper),
                    truncateString(responseBody));

            responseWrapper.copyBodyToResponse();
        }
    }

    /**
     * Extracts and formats request headers, anonymizing the Authorization token.
     */
    private String getHeaders(HttpServletRequest request) {
        StringBuilder headers = new StringBuilder();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            if (headerName.equalsIgnoreCase("authorization")) {
                headerValue = "Bearer [REDACTED]";
            }
            headers.append(headerName).append(": ").append(headerValue).append(", ");
        }
        return headers.toString();
    }

    /**
     * Extracts and formats response headers.
     */
    private String getResponseHeaders(HttpServletResponse response) {
        StringBuilder headers = new StringBuilder();
        Collection<String> headerNames = response.getHeaderNames();
        for (String headerName : headerNames) {
            String headerValue = response.getHeader(headerName);
            headers.append(headerName).append(": ").append(headerValue).append(", ");
        }
        return headers.toString();
    }

    private String getStringValue(byte[] contentAsByteArray, String characterEncoding) {
        try {
            return new String(contentAsByteArray, characterEncoding);
        } catch (Exception e) {
            return new String(contentAsByteArray, StandardCharsets.UTF_8);
        }
    }


    /**
     * Truncates a string if it exceeds the maximum allowed length.
     */
    private String truncateString(String value) {
        if (value == null || value.length() <= MAX_PAYLOAD_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_PAYLOAD_LENGTH) + "... (truncated)";
    }
}