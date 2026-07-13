package com.zhilu.delivery.iam.service;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class OidcLoginSuccessHandler implements AuthenticationSuccessHandler {
  private final OidcIdentityService identities;

  public OidcLoginSuccessHandler(OidcIdentityService identities) {
    this.identities = identities;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication) throws IOException, ServletException {
    OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
    OAuth2User principal = token.getPrincipal();
    String subject = stringAttribute(principal, "sub");
    if (subject == null) {
      subject = principal.getName();
    }
    String email = stringAttribute(principal, "email");
    Object verifiedValue = principal.getAttribute("email_verified");
    boolean verified = Boolean.TRUE.equals(verifiedValue)
        || "true".equalsIgnoreCase(String.valueOf(verifiedValue));
    CurrentUser user = identities.authenticate(
        token.getAuthorizedClientRegistrationId(), subject, email, verified);
    request.getSession(true).setAttribute(CurrentUser.SESSION_KEY, user);
    response.sendRedirect("/");
  }

  private String stringAttribute(OAuth2User principal, String name) {
    Object value = principal.getAttribute(name);
    return value == null ? null : String.valueOf(value);
  }
}
