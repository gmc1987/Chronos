package com.chronos.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.security.Key;
import java.util.Date;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {
	private Key key;
	private long expirationMs;
	private long refreshExpirationMs;

	@Autowired
	public JwtUtil(Environment env) {
		String secret = env.getProperty("security.jwt.secret", "replace-with-a-very-secure-secret");
		this.expirationMs = Long.parseLong(env.getProperty("security.jwt.expiration-ms", "86400000"));
		this.refreshExpirationMs = Long.parseLong(env.getProperty("security.jwt.refresh-expiration-ms", "604800000"));
		this.key = new SecretKeySpec(secret.getBytes(), SignatureAlgorithm.HS256.getJcaName());
	}

	public String generateAccessToken(String subject, Map<String, Object> claims, long ttlMillis) {
		long nowMillis = System.currentTimeMillis();
		Date now = new Date(nowMillis);
		return Jwts.builder().setClaims(claims).setSubject(subject).setIssuedAt(now)
				.signWith(this.key, SignatureAlgorithm.HS256)
				.setExpiration(new Date(nowMillis + ((ttlMillis > 0L) ? ttlMillis : this.expirationMs))).compact();
	}

	public String generateAccessToken(String subject, Map<String, Object> claims) {
		return generateAccessToken(subject, claims, this.expirationMs);
	}

	public String generateRefreshToken(String subject, Map<String, Object> claims) {
		long nowMillis = System.currentTimeMillis();
		Date now = new Date(nowMillis);
		return Jwts.builder().setClaims(claims).setSubject(subject).setIssuedAt(now)
				.signWith(this.key, SignatureAlgorithm.HS256)
				.setExpiration(new Date(nowMillis + this.refreshExpirationMs)).compact();
	}

	public Claims parseToken(String token) throws ExpiredJwtException {
		return (Claims) Jwts.parserBuilder().setSigningKey(this.key).build().parseClaimsJws(token).getBody();
	}

	public boolean isTokenExpired(String token) {
		try {
			Claims claims = parseToken(token);
			return claims.getExpiration().before(new Date());
		} catch (ExpiredJwtException e) {
			return true;
		}
	}
}
