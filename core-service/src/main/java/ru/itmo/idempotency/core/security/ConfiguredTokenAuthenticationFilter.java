package ru.itmo.idempotency.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.itmo.idempotency.core.config.CoreProperties;

import java.io.IOException;
import java.util.List;

@Component
public class ConfiguredTokenAuthenticationFilter extends OncePerRequestFilter {

    private final CoreProperties coreProperties;

    public ConfiguredTokenAuthenticationFilter(CoreProperties coreProperties) {
        this.coreProperties = coreProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring("Bearer ".length());
            coreProperties.getSecurity().getTokens().stream()
                    .filter(config -> config.getToken().equals(token))
                    .findFirst()
                    .ifPresent(config -> SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    config.getPrincipal(),
                                    token,
                                    config.getRoles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList()
                            )
                    ));
        }

        filterChain.doFilter(request, response);
    }
}
