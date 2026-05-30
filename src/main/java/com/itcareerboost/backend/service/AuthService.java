package com.itcareerboost.backend.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
  private final String adminEmail;
  private final String adminPassword;
  private final String adminToken;

  public AuthService(
      @Value("${app.admin.email}") String adminEmail,
      @Value("${app.admin.password}") String adminPassword) {
    this.adminEmail = adminEmail;
    this.adminPassword = adminPassword;
    this.adminToken = Base64.getUrlEncoder().withoutPadding()
        .encodeToString((adminEmail + ":" + Instant.now()).getBytes(StandardCharsets.UTF_8));
  }

  public String login(String email, String password) {
    if (adminEmail.equalsIgnoreCase(email) && adminPassword.equals(password)) {
      return adminToken;
    }
    throw new UnauthorizedException("Invalid admin credentials.");
  }

  public void requireAdmin(String authorizationHeader) {
    if (authorizationHeader == null || !authorizationHeader.equals("Bearer " + adminToken)) {
      throw new UnauthorizedException("Admin authentication is required.");
    }
  }

  public String adminEmail() {
    return adminEmail;
  }
}
