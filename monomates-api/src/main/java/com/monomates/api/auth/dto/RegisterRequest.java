package com.monomates.api.auth.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(
  @NotBlank @Size(max = 150) String fullName,
  @NotBlank @Email @Size(max = 255) String email,
  @NotBlank @Size(min = 8, max = 72) String password
) {}
