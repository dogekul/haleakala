package com.zhilu.delivery.iam.service;

import com.zhilu.delivery.iam.domain.AppUser;

public final class UserSummary {
  private final Long id;
  private final String username;
  private final String displayName;
  private final String email;
  private final String status;

  public UserSummary(AppUser user) {
    this.id = user.getId();
    this.username = user.getUsername();
    this.displayName = user.getDisplayName();
    this.email = user.getEmail();
    this.status = user.getStatus();
  }

  public Long getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getEmail() {
    return email;
  }

  public String getStatus() {
    return status;
  }
}

