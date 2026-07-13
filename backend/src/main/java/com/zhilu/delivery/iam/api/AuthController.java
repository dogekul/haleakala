package com.zhilu.delivery.iam.api;

import com.zhilu.delivery.iam.service.CurrentUser;
import com.zhilu.delivery.iam.service.IamService;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final IamService iam;

  public AuthController(IamService iam) {
    this.iam = iam;
  }

  @PostMapping("/login")
  public CurrentUser login(@Valid @RequestBody LoginRequest request, HttpSession session) {
    CurrentUser user = iam.authenticate(request.getUsername(), request.getPassword());
    session.setAttribute(CurrentUser.SESSION_KEY, user);
    establishSecurityContext(user, session);
    return user;
  }

  private void establishSecurityContext(CurrentUser user, HttpSession session) {
    List<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();
    for (String permission : user.getPermissions()) {
      authorities.add(new SimpleGrantedAuthority(permission));
    }
    for (String role : user.getRoles()) {
      authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
    }
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        new UsernamePasswordAuthenticationToken(user, null, authorities));
    SecurityContextHolder.setContext(context);
    session.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
  }

  @GetMapping("/me")
  public CurrentUser me(HttpSession session) {
    Object current = session.getAttribute(CurrentUser.SESSION_KEY);
    return current instanceof CurrentUser ? (CurrentUser) current : null;
  }

  @PostMapping("/logout")
  public void logout(HttpSession session) {
    session.invalidate();
  }

  public static final class LoginRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }
  }
}
