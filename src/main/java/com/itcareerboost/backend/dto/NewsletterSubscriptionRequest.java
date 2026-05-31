package com.itcareerboost.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record NewsletterSubscriptionRequest(
    @NotBlank @Email String email,
    String source) {}
