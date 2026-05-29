package pl.michalbzowski.windband.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Configuration
public class JwtConfig {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    @Bean
    public SecretKey jwtSecretKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username, Map<String, Object> extraClaims) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        var builder = Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiry);

        if (extraClaims != null) {
            extraClaims.forEach(builder::claim);
        }

        return builder.signWith(jwtSecretKey()).compact();
    }

    public String generateToken(String username) {
        return generateToken(username, null);
    }

    public String extractUsername(String token) {
        return Jwts.parser()
                .verifyWith(jwtSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(jwtSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extractClaimAsLong(String token, String claimName) {
        Object value = extractAllClaims(token).get(claimName);
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(value.toString());
    }

    public String extractClaimAsString(String token, String claimName) {
        Object value = extractAllClaims(token).get(claimName);
        return value != null ? value.toString() : null;
    }

    @SuppressWarnings("unchecked")
    public java.util.List<Long> extractTeamIds(String token) {
        Object value = extractAllClaims(token).get("teamIds");
        if (value instanceof java.util.List<?> list) {
            return list.stream()
                    .map(v -> v instanceof Number n ? n.longValue() : Long.parseLong(v.toString()))
                    .collect(java.util.stream.Collectors.toList());
        }
        return java.util.List.of();
    }

    public boolean isTokenValid(String token) {
        try {
            Jwts.parser()
                    .verifyWith(jwtSecretKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
