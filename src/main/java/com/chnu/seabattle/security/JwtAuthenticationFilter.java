package com.chnu.seabattle.security;

import com.chnu.seabattle.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
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
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String jwt = extractCookie(request, "accessToken");
        if (jwt == null) {
            tryRefresh(request, response);
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (jwtService.isTokenExpired(jwt)) {
                log.warn("Access token expired, attempting silent refresh");
                tryRefresh(request, response);
                filterChain.doFilter(request, response);
                return;
            }
            final String userLogin = jwtService.getUsernameFromToken(jwt);

            log.debug("Processing JWT authentication for user: {}", userLogin);

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (userLogin != null && authentication == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(userLogin);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

                    authToken.setDetails(new WebAuthenticationDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.info("Successfully authenticated user: {}", userLogin);
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

    private void tryRefresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractCookie(request, "refreshToken");
        if (refreshToken == null || jwtService.isTokenExpired(refreshToken)) {
            log.warn("Refresh token missing or expired, user must re-login");
            return;
        }

        String username = jwtService.getUsernameFromToken(refreshToken);
        if (username == null) {
            log.warn("Could not extract username from refresh token");
            return;
        }
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        if (jwtService.isTokenValid(refreshToken, userDetails)) {

            response.addHeader(HttpHeaders.SET_COOKIE,
                    jwtService.generateAccessToken(userDetails)
            );

            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities()
            );
            authToken.setDetails(new WebAuthenticationDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
            log.info("Silently refreshed access token for user: {}", username);
        }
    }

    private String extractCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }

        return Arrays.stream(cookies)
                .filter(c -> cookieName.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}