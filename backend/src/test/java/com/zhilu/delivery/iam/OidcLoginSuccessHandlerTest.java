package com.zhilu.delivery.iam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhilu.delivery.iam.service.CurrentUser;
import com.zhilu.delivery.iam.service.OidcIdentityService;
import com.zhilu.delivery.iam.service.OidcLoginSuccessHandler;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

class OidcLoginSuccessHandlerTest {
  @Test
  void storesMappedUserInSessionAndReturnsToApplication() throws Exception {
    OidcIdentityService identities = mock(OidcIdentityService.class);
    OAuth2AuthenticationToken authentication = mock(OAuth2AuthenticationToken.class);
    OAuth2User principal = mock(OAuth2User.class);
    CurrentUser user = new CurrentUser(
        7L, 1L, "sso-user", "单点用户", Arrays.asList("PMO"),
        Collections.singletonList("project:read"));
    when(authentication.getAuthorizedClientRegistrationId()).thenReturn("feishu");
    when(authentication.getPrincipal()).thenReturn(principal);
    when(principal.getAttribute("sub")).thenReturn("open-id-7");
    when(principal.getAttribute("email")).thenReturn("sso@example.com");
    when(principal.getAttribute("email_verified")).thenReturn(Boolean.TRUE);
    when(identities.authenticate("feishu", "open-id-7", "sso@example.com", true))
        .thenReturn(user);

    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    new OidcLoginSuccessHandler(identities).onAuthenticationSuccess(
        request, response, authentication);

    verify(identities).authenticate("feishu", "open-id-7", "sso@example.com", true);
    assertEquals(user, request.getSession().getAttribute(CurrentUser.SESSION_KEY));
    SecurityContext context = (SecurityContext) request.getSession().getAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    assertNotNull(context);
    assertEquals(user, context.getAuthentication().getPrincipal());
    assertTrue(context.getAuthentication().getAuthorities().stream()
        .anyMatch(authority -> "project:read".equals(authority.getAuthority())));
    assertTrue(context.getAuthentication().getAuthorities().stream()
        .anyMatch(authority -> "ROLE_PMO".equals(authority.getAuthority())));
    assertEquals(user, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    assertEquals("/", response.getRedirectedUrl());
    SecurityContextHolder.clearContext();
  }
}
