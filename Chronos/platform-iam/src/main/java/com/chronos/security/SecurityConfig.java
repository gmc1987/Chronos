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
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.Filter;

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
	      .authorizeHttpRequests(auth -> ((AuthorizeHttpRequestsConfigurer.AuthorizedUrl)((AuthorizeHttpRequestsConfigurer.AuthorizedUrl)((AuthorizeHttpRequestsConfigurer.AuthorizedUrl)((AuthorizeHttpRequestsConfigurer.AuthorizedUrl)((AuthorizeHttpRequestsConfigurer.AuthorizedUrl)((AuthorizeHttpRequestsConfigurer.AuthorizedUrl)((AuthorizeHttpRequestsConfigurer.AuthorizedUrl)((AuthorizeHttpRequestsConfigurer.AuthorizedUrl)((AuthorizeHttpRequestsConfigurer.AuthorizedUrl)((AuthorizeHttpRequestsConfigurer.AuthorizedUrl)((AuthorizeHttpRequestsConfigurer.AuthorizedUrl)auth.requestMatchers(HttpMethod.OPTIONS, new String[] { "/**" })).permitAll().requestMatchers(HttpMethod.POST, new String[] { "/auth/login" })).permitAll().requestMatchers(HttpMethod.POST, new String[] { "/consumer/users/register" })).permitAll().requestMatchers(HttpMethod.POST, new String[] { "/consumer/users/register/sms" })).permitAll().requestMatchers(HttpMethod.POST, new String[] { "/consumer/users/register/oauth" })).permitAll().requestMatchers(HttpMethod.POST, new String[] { "/consumer/users/login" })).permitAll().requestMatchers(HttpMethod.GET, new String[] { "/dicts/**" })).permitAll().requestMatchers(HttpMethod.GET, new String[] { "/ai/task/videoProxy" })).permitAll().requestMatchers(HttpMethod.GET, new String[] { "/video/**" })).permitAll().requestMatchers(new String[] { "/actuator/**" })).permitAll().anyRequest()).authenticated()).sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
	      .addFilterBefore((Filter)jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
	      .httpBasic(b -> b.disable());
	    return (SecurityFilterChain)http.build();
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
