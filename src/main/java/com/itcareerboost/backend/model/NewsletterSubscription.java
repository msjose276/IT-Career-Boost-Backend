package com.itcareerboost.backend.model;

import java.time.Instant;

public record NewsletterSubscription(String email, Instant subscribedAt, String source) {}
