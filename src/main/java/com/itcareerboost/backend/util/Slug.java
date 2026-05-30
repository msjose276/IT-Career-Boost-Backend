package com.itcareerboost.backend.util;

import java.text.Normalizer;
import java.util.Locale;

public final class Slug {
  private Slug() {}

  public static String from(String value) {
    String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "");
    return normalized
        .toLowerCase(Locale.US)
        .trim()
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-+|-+$", "");
  }
}
