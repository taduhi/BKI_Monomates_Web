package com.monomates.api.auth.dto;

import com.monomates.api.user.dto.UserResponse;
import java.time.Instant;

public record AuthResponse(UserResponse user, Instant expiresAt) {}
