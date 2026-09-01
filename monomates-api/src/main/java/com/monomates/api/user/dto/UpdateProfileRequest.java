package com.monomates.api.user.dto;

import jakarta.validation.constraints.*;

public record UpdateProfileRequest(
  @NotBlank @Size(max = 150) String fullName
) {}
