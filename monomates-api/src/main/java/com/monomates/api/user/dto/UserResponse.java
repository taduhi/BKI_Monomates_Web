package com.monomates.api.user.dto;

import com.monomates.api.user.UserAccount;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
  UUID id,
  String email,
  String fullName,
  String role,
  String status,
  Instant createdAt
) {
  public static UserResponse from(UserAccount u) {
    return new UserResponse(
      u.getId(),
      u.getEmail(),
      u.getFullName(),
      u.getRole().name(),
      u.getStatus().name(),
      u.getCreatedAt()
    );
  }
}
