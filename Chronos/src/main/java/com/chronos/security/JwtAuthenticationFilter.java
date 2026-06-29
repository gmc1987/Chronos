package com.chronos.security;

import com.chronos.Idao.IConsumerUserRepository;
import com.chronos.commons.model.ResultData;
import com.chronos.model.pojo.ConsumerUser;
import com.chronos.security.AdminUserDetailsService;
import com.chronos.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
	@Autowired
	private AdminUserDetailsService userDetailsService;
	@Autowired
	private IConsumerUserRepository consumerUserRepository;
	@Autowired
	private JwtUtil jwtUtil;
	private ObjectMapper objectMapper = new ObjectMapper();

	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String bearer = request.getHeader("Authorization");
		if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
			String token = bearer.substring(7);
			try {
				Claims claims = this.jwtUtil.parseToken(token);
				String username = claims.getSubject();
				String type = (String) claims.get("type", String.class);
				if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
					if ("consumer".equalsIgnoreCase(type)) {
						ConsumerUser cu = this.consumerUserRepository.findByUsername(username);
						if (cu == null) {
							cu = this.consumerUserRepository.findByPhone(username);
						}
						if (cu != null) {
							User userDetails = new User(username, "",
									List.of(new SimpleGrantedAuthority("ROLE_CONSUMER")));
							UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
									userDetails, null, userDetails.getAuthorities());
							auth.setDetails((new WebAuthenticationDetailsSource()).buildDetails(request));
							SecurityContextHolder.getContext().setAuthentication((Authentication) auth);
						}
					} else {
						UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
						UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails,
								null, userDetails.getAuthorities());
						auth.setDetails((new WebAuthenticationDetailsSource()).buildDetails(request));
						SecurityContextHolder.getContext().setAuthentication((Authentication) auth);
					}
				}
			} catch (ExpiredJwtException ex) {

				response.setStatus(401);
				response.setContentType("application/json;charset=UTF-8");
				ResultData<String> rd = ResultData.<String>builder().code("401").msg("token expired").data(null)
						.build();
				response.getWriter().write(this.objectMapper.writeValueAsString(rd));
				return;
			} catch (Exception exception) {
			}
		}

		filterChain.doFilter((ServletRequest) request, (ServletResponse) response);
	}
}
