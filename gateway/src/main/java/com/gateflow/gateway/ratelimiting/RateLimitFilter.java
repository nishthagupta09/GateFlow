package com.gateflow.gateway.ratelimiting;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;

    public RateLimitFilter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String clientId = request.getRemoteAddr();

        boolean allowed =
                rateLimiter.isAllowed(clientId);

        if (!allowed) {

            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("""
                    {
                        "error": "Too Many Requests",
                        "message": "Rate limit exceeded"
                    }
                    """);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
