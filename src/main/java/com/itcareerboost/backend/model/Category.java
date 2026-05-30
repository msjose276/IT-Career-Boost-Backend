package com.itcareerboost.backend.model;

import jakarta.validation.constraints.NotBlank;

public record Category(@NotBlank String id, @NotBlank String name, @NotBlank String description) {}
