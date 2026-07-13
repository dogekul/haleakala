package com.zhilu.delivery.iam.domain;

import com.zhilu.delivery.common.model.BaseEntity;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "app_user")
public class AppUser extends BaseEntity {
  @Column(name = "organization_id", nullable = false)
  private Long organizationId;

  @Column(name = "primary_team_id")
  private Long primaryTeamId;

  @Column(nullable = false, length = 80)
  private String username;

  @Column(name = "password_hash", length = 100)
  private String passwordHash;

  @Column(name = "display_name", nullable = false, length = 120)
  private String displayName;

  @Column(length = 160)
  private String email;

  @Column(nullable = false, length = 24)
  private String status;

  protected AppUser() {}

  public Long getOrganizationId() {
    return organizationId;
  }

  public Long getPrimaryTeamId() {
    return primaryTeamId;
  }

  public String getUsername() {
    return username;
  }

  public String getPasswordHash() {
    return passwordHash;
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

