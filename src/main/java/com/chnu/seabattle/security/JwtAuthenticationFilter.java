package com.chnu.seabattle.security;

import com.chnu.seabattle.service.serviceImpl.JwtServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
@Log4j2
public class JwtAuthenticationFilter extends OncePerRequestFilter {


    private final HandlerExceptionResolver handlerExceptionResolver;

    private final JwtServiceImpl jwtServiceImpl;

    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain
    ) throws ServletException, IOException {

        // Try to extract JWT from Authorization header first
        String jwt = extractJwtFromHeader(request);

        // If not found in header, try to extract from cookies
        if (jwt == null) {
            jwt = extractJwtFromCookies(request);
        }

        // If no JWT found in either location, continue without authentication
        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (jwtServiceImpl.isTokenExpired(jwt)) {
                log.warn("JWT token is expired");
                response.sendError(401);
                return;
            }
            final String userLogin = jwtServiceImpl.getUsernameFromToken(jwt);

            log.debug("Processing JWT authentication for user: {}", userLogin);

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (userLogin != null && authentication == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(userLogin);

                if (jwtServiceImpl.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

                    authToken.setDetails(new WebAuthenticationDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("Successfully authenticated user: {}", userLogin);
                } else {
                    log.warn("Invalid JWT token for user: {}", userLogin);
                }
            }

            filterChain.doFilter(request, response);
        } catch (Exception e) {
            log.error("JWT authentication failed", e);
            handlerExceptionResolver.resolveException(request, response, null, e);
        }
    }

    /**
     * Extract JWT token from Authorization header
     */
    private String extractJwtFromHeader(HttpServletRequest request) {
        final String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        return null;
    }

    /**
     * Extract JWT token from cookies (looks for 'accessToken' cookie)
     */
    private String extractJwtFromCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }

        return Arrays.stream(cookies)
                .filter(cookie -> "accessToken".equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
