package com.chronos.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
	@Bean
	public PasswordEncoder passwordEncoder() {
		return (PasswordEncoder) new BCryptPasswordEncoder();
	}

	@Autowired
	private AdminUserDetailsService userDetailsService;
	@Value("${security.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
	private String allowedOriginPatterns;

	@Bean
	public JwtAuthenticationFilter jwtAuthenticationFilter() {
		return new JwtAuthenticationFilter();
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						// Refresh and revoke are authenticated by possession of the refresh token.
						// Requiring an access token here makes an expired session impossible to renew
						// and prevents clients from revoking the refresh token during logout.
						.requestMatchers(HttpMethod.POST, "/auth/login", "/auth/refresh", "/auth/revoke")
						.permitAll()
						.requestMatchers(HttpMethod.POST, "/consumer/users/register").permitAll()
						.requestMatchers(HttpMethod.POST, "/consumer/users/register/sms").permitAll()
						.requestMatchers(HttpMethod.POST, "/consumer/users/register/oauth").permitAll()
						.requestMatchers(HttpMethod.POST, "/consumer/users/login").permitAll()
						.requestMatchers(HttpMethod.POST,
								"/api/mini-program/auth/login",
								"/api/mini-program/auth/bind-profile",
								"/api/mini-program/auth/bind-guardian")
						.permitAll()
						.requestMatchers(HttpMethod.GET, "/dicts/**").permitAll()
						.requestMatchers(
								HttpMethod.GET,
								"/public/industry/context",
								"/api/public/industry/context")
						.permitAll()
						.requestMatchers(HttpMethod.GET, "/ai/task/videoProxy").permitAll()
						.requestMatchers(HttpMethod.GET, "/video/**").permitAll()
						.requestMatchers("/actuator/**").permitAll()
						.anyRequest().authenticated())
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.addFilterBefore(
						jwtAuthenticationFilter(),
						UsernamePasswordAuthenticationFilter.class)
				.httpBasic(httpBasic -> httpBasic.disable());
		return http.build();
	}
	  
	  @Bean
	  public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
	    return authenticationConfiguration.getAuthenticationManager();
	  }
	  
	  @Bean
	  public CorsConfigurationSource corsConfigurationSource() {
	    CorsConfiguration config = new CorsConfiguration();
	    config.setAllowedOriginPatterns(List.of(allowedOriginPatterns.split("\\s*,\\s*")));
	    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
	    config.setAllowedHeaders(List.of("*"));
	    config.setAllowCredentials(Boolean.valueOf(true));
	    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
	    source.registerCorsConfiguration("/**", config);
	    return (CorsConfigurationSource)source;
	  }
}
