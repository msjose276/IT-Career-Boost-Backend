package com.itcareerboost.backend.controller;

import com.itcareerboost.backend.dto.AuthDtos.LoginRequest;
import com.itcareerboost.backend.dto.AuthDtos.LoginResponse;
import com.itcareerboost.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    return new LoginResponse(authService.login(request.email(), request.password()), authService.adminEmail());
  }
}
