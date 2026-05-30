package com.itcareerboost.backend.model;

import java.time.Instant;

public record SearchEvent(String q, Instant at) {}
