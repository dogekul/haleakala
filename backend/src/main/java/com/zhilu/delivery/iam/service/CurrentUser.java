package com.zhilu.delivery.iam.service;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public final class CurrentUser implements Serializable {
  public static final String SESSION_KEY = "DELIVERY_CURRENT_USER";
  private static final long serialVersionUID = 1L;

  private final Long id;
  private final Long organizationId;
  private final String username;
  private final String displayName;
  private final List<String> roles;
  private final List<String> permissions;

  public CurrentUser(
      Long id,
      Long organizationId,
      String username,
      String displayName,
      List<String> roles,
      List<String> permissions) {
    this.id = id;
    this.organizationId = organizationId;
    this.username = username;
    this.displayName = displayName;
    this.roles = Collections.unmodifiableList(roles);
    this.permissions = Collections.unmodifiableList(permissions);
  }

  public Long getId() {
    return id;
  }

  public Long getOrganizationId() {
    return organizationId;
  }

  public String getUsername() {
    return username;
  }

  public String getDisplayName() {
    return displayName;
  }

  public List<String> getRoles() {
    return roles;
  }

  public List<String> getPermissions() {
    return permissions;
  }
}

