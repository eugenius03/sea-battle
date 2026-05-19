package com.chnu.seabattle.security;

import com.chnu.seabattle.service.JwtService;
import com.chnu.seabattle.service.RefreshTokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
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
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
@Log4j2
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            String jwt = extractCookie(request, "accessToken");

            if (jwt == null) {
                tryRefresh(request, response);
            } else {
                Claims claims = jwtService.validateAndExtractClaims(jwt, "access");
                String username = claims.getSubject();
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

                if (username != null && authentication == null) {

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            username, null, null
                    );
                    authToken.setDetails(new WebAuthenticationDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (ExpiredJwtException e) {
            tryRefresh(request, response);
        } catch (JwtException e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            jwtService.clearAuthCookies(response);
        }

        filterChain.doFilter(request, response);
    }

    private void tryRefresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractCookie(request, "refreshToken");
        if (refreshToken == null) {
            return;
        }
        try {
            UserDetails userDetails = refreshTokenService.validateAndRotateRefreshToken(refreshToken);

            response.addHeader(HttpHeaders.SET_COOKIE, jwtService.createAccessCookie(userDetails).toString());
            response.addHeader(HttpHeaders.SET_COOKIE, jwtService.createRefreshCookie(userDetails).toString());

            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities()
            );
            authToken.setDetails(new WebAuthenticationDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
            log.info("Silently refreshed access token for user: {}", userDetails.getUsername());
        } catch (UsernameNotFoundException e) {
            log.warn("User no longer exists, clearing cookies");
            jwtService.clearAuthCookies(response);
        } catch (ExpiredJwtException e) {
            log.warn("Refresh token expired, user must re-login");
            jwtService.clearAuthCookies(response);
        } catch (JwtException e) {
            log.warn("Invalid refresh token: {}", e.getMessage());
            jwtService.clearAuthCookies(response);
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