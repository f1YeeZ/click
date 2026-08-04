package com.clicker.mousehub.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.clicker.mousehub.entity.AuthSession;
import com.clicker.mousehub.entity.UserAccount;
import com.clicker.mousehub.mapper.AuthSessionMapper;
import com.clicker.mousehub.mapper.UserMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final AuthSessionMapper sessionMapper;

    public JwtAuthenticationFilter(JwtService jwtService, UserMapper userMapper, AuthSessionMapper sessionMapper) {
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.sessionMapper = sessionMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                JwtService.JwtPrincipal principal = jwtService.principal(header.substring(7));
                UserAccount user = userMapper.selectOne(Wrappers.<UserAccount>lambdaQuery().eq(UserAccount::getEmail, principal.email()));
                AuthSession session = principal.sessionId() == null ? null : sessionMapper.selectById(principal.sessionId());
                if (user != null && "ACTIVE".equals(user.getStatus())
                        && user.getTokenVersion() == principal.tokenVersion()
                        && session != null && session.getUserId().equals(user.getId())
                        && session.getTokenVersion() == principal.tokenVersion()
                        && session.getRevokedAt() == null && session.getExpiresAt().isAfter(java.time.OffsetDateTime.now())) {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            user.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
