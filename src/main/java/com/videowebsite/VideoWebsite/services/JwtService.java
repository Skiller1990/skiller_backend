package com.videowebsite.VideoWebsite.services;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {
    private static final String SECRET_KEY = "RixBErfMc+ENuvj1WTRhGSWb5EVHq+JQ7GBYTfna/l4=";
    private static final long EXPIRATION_TIME = 1000 * 60 * 60 * 24; // 24 hour

    public String generateToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private Key getSigningKey() {
        byte[] keyBytes = Base64.getDecoder().decode(SECRET_KEY);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String extractUserName(String token) {
        return extractClaims(token, Claims::getSubject);
    }

    public boolean validateToken(String token, String userEmail) {
        String userName = extractUserName(token);
        return userName.equals(userEmail) && !isTokenExpired(token);
    }

    public boolean isTokenExpired(String token) {
        return extractClaims(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaims(String token, Function<Claims, T> claimsResolver) {
        System.out.println("Token : "+token);
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claimsResolver.apply(claims);
    }
}