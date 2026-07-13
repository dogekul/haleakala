package com.zhilu.delivery.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhilu.delivery.common.api.ApiError;
import com.zhilu.delivery.iam.service.CurrentUser;
import com.zhilu.delivery.iam.service.IamService;
import com.zhilu.delivery.iam.service.OidcLoginSuccessHandler;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
  private final ObjectMapper objectMapper;
  private final SessionCurrentUserFilter sessionCurrentUserFilter;
  private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;
  private final OidcLoginSuccessHandler oidcLoginSuccessHandler;

  public SecurityConfig(
      ObjectMapper objectMapper,
      SessionCurrentUserFilter sessionCurrentUserFilter,
      ObjectProvider<ClientRegistrationRepository> clientRegistrations,
      OidcLoginSuccessHandler oidcLoginSuccessHandler) {
    this.objectMapper = objectMapper;
    this.sessionCurrentUserFilter = sessionCurrentUserFilter;
    this.clientRegistrations = clientRegistrations;
    this.oidcLoginSuccessHandler = oidcLoginSuccessHandler;
  }

  @Bean
  public static PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .httpBasic().disable()
        .formLogin().disable()
        .csrf()
        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
        .ignoringAntMatchers(
            "/api/v1/auth/login",
            "/api/v1/auth/logout",
            "/api/v1/integrations/agent/events")
        .and()
        .authorizeRequests()
        .antMatchers(
            "/actuator/health",
            "/actuator/info",
            "/api/v1/auth/login",
            "/oauth2/**",
            "/login/oauth2/**",
            "/v3/api-docs/**",
            "/swagger-ui/**")
        .permitAll()
        .antMatchers(HttpMethod.GET,
            "/api/v1/admin/audit-logs", "/api/v1/admin/audit-log-facets")
        .access("hasAuthority('audit:read') or hasAuthority('system:manage')")
        .antMatchers("/api/v1/admin/**").hasAuthority("system:manage")
        .antMatchers(HttpMethod.GET, "/api/v1/products/**").hasAuthority("product:read")
        .antMatchers("/api/v1/products/**").hasAuthority("product:write")
        .antMatchers("/api/v1/dashboard/**").hasAuthority("dashboard:read")
        .antMatchers(HttpMethod.GET, "/api/v1/requirements/**").hasAuthority("requirement:read")
        .antMatchers("/api/v1/requirements/**").hasAuthority("requirement:write")
        .antMatchers(HttpMethod.POST, "/api/v1/standardization/debts/from-requirement")
        .access("hasAuthority('requirement:write') or hasAuthority('standardization:write')")
        .antMatchers(HttpMethod.GET, "/api/v1/standardization/**").hasAuthority("standardization:read")
        .antMatchers("/api/v1/standardization/**").hasAuthority("standardization:write")
        .antMatchers(HttpMethod.GET, "/api/v1/knowledge/**").hasAuthority("knowledge:read")
        .antMatchers("/api/v1/knowledge/**").hasAuthority("knowledge:write")
        .antMatchers(HttpMethod.GET, "/api/v1/resources/**").hasAuthority("resource:read")
        .antMatchers("/api/v1/resources/**").hasAuthority("resource:write")
        .antMatchers("/api/v1/integrations/agent/events").permitAll()
        .antMatchers(HttpMethod.GET, "/api/v1/agent-jobs/**").hasAuthority("project:read")
        .antMatchers("/api/v1/agent-jobs/**").hasAuthority("project:write")
        .antMatchers(HttpMethod.GET, "/api/v1/projects/**").hasAuthority("project:read")
        .antMatchers("/api/v1/projects/**").hasAuthority("project:write")
        .anyRequest().authenticated()
        .and()
        .exceptionHandling()
        .authenticationEntryPoint((request, response, error) ->
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "请先登录"))
        .accessDeniedHandler((request, response, error) ->
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "无权执行此操作"));
    if (clientRegistrations.getIfAvailable() != null) {
      http.oauth2Login().successHandler(oidcLoginSuccessHandler);
    }
    http.addFilterBefore(sessionCurrentUserFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  private void writeError(HttpServletResponse response, int status, String code, String message)
      throws IOException {
    response.setStatus(status);
    response.setCharacterEncoding("UTF-8");
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getOutputStream(),
        new ApiError(code, message, UUID.randomUUID().toString(), Collections.emptyMap()));
  }

  @Component
  public static class SessionCurrentUserFilter extends OncePerRequestFilter {
    private final IamService iam;

    public SessionCurrentUserFilter(IamService iam) {
      this.iam = iam;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
      HttpSession session = request.getSession(false);
      Object value = session == null ? null : session.getAttribute(CurrentUser.SESSION_KEY);
      if (value instanceof CurrentUser) {
        try {
          CurrentUser user = iam.currentUser(((CurrentUser) value).getId());
          session.setAttribute(CurrentUser.SESSION_KEY, user);
          List<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();
          for (String permission : user.getPermissions()) {
            authorities.add(new SimpleGrantedAuthority(permission));
          }
          for (String role : user.getRoles()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
          }
          UsernamePasswordAuthenticationToken authentication =
              new UsernamePasswordAuthenticationToken(user, null, authorities);
          SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (AuthenticationException disabledOrMissing) {
          session.removeAttribute(CurrentUser.SESSION_KEY);
          session.removeAttribute(
              HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
          SecurityContextHolder.clearContext();
        }
      }
      filterChain.doFilter(request, response);
    }
  }
}
