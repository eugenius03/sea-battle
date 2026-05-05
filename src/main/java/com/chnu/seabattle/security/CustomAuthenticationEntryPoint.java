package com.chnu.seabattle.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         @NonNull HttpServletResponse response,
                         @NonNull AuthenticationException authException) throws IOException {

        String requestUri = request.getRequestURI();

        if (requestUri.contains("/api/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Unauthorized - Please login first\"}");
        } else {
            String redirect = requestUri;
            String query = request.getQueryString();
            if (query != null && !query.isEmpty()) {
                redirect += "?" + query;
            }
            response.sendRedirect("/login?redirect=" + URLEncoder.encode(redirect, StandardCharsets.UTF_8));
        }
    }
}

