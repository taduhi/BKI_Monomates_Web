package com.monomates.api.user;

import com.monomates.api.common.model.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "app_users")
public class UserAccount extends BaseEntity {

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "full_name", nullable = false)
  private String fullName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserRole role;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserStatus status;

  protected UserAccount() {}

  public UserAccount(String e, String p, String n, UserRole r, UserStatus s) {
    email = e;
    passwordHash = p;
    fullName = n;
    role = r;
    status = s;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getFullName() {
    return fullName;
  }

  public UserRole getRole() {
    return role;
  }

  public UserStatus getStatus() {
    return status;
  }

  public void setFullName(String n) {
    fullName = n;
  }
}
