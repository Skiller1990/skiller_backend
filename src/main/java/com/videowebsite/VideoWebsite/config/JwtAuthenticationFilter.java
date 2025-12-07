package com.videowebsite.VideoWebsite.config;

import com.videowebsite.VideoWebsite.Entities.model.User;
import com.videowebsite.VideoWebsite.services.JwtService;
import com.videowebsite.VideoWebsite.services.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserService userService;
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
          final String authHeader = request.getHeader("Authorization");
          final String jwtToken;
          final String userEmail;

          if(StringUtils.isEmpty(authHeader) || !authHeader.startsWith("Bearer")){
              filterChain.doFilter(request,response);
              return;
          }

          jwtToken = authHeader.substring(7);
          userEmail = jwtService.extractUserName(jwtToken);

        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                User user = userService.findByEmail(userEmail);
                if (user != null && jwtService.validateToken(jwtToken, user.getEmail())) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            user, null, Collections.emptyList()
                    );
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (Exception e) {
                // Optional: Log or handle errors (e.g. user not found, token invalid)
            }
        }

        filterChain.doFilter(request, response);
    }
}
