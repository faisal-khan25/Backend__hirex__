package com.hirex.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration}")
    private long expiration;

    private Key getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    // generate token from email
    public String generateToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }


    public String getEmail(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getKey()).build()
                    .parseClaimsJws(token).getBody().getSubject();
        } catch (JwtException e) {
            return null; // JwtFilter already handles null
        }
    }

    public boolean isValid(String token, UserDetails userDetails) {
        String email = getEmail(token);
        if (email == null) return false;  // ← guard added
        return email.equals(userDetails.getUsername()) && !isExpired(token);
    }


    //    private boolean isExpired(String token) {
//        return Jwts.parserBuilder()
//                .setSigningKey(getKey())
//                .build()
//                .parseClaimsJws(token)
//                .getBody()
//                .getExpiration()
//                .before(new Date());
//    }
private boolean isExpired(String token) {
    try {
        return Jwts.parserBuilder()
                .setSigningKey(getKey()).build()
                .parseClaimsJws(token).getBody()
                .getExpiration().before(new Date());
    } catch (ExpiredJwtException e) {
        return true; // ✅ treat expired as expired
    }
}
}
