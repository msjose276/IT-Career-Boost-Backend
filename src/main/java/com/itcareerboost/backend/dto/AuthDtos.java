package com.itcareerboost.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {
  private AuthDtos() {}

  public record LoginRequest(@Email String email, @NotBlank String password) {}

  public record LoginResponse(String token, String email) {}
}
