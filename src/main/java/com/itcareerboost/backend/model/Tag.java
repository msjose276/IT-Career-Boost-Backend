package com.itcareerboost.backend.model;

import jakarta.validation.constraints.NotBlank;

public record Tag(@NotBlank String id, @NotBlank String name) {}
