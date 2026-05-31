package com.itcareerboost.backend.dto;

import com.itcareerboost.backend.model.Article;
import com.itcareerboost.backend.model.SearchEvent;
import java.util.List;

public record DashboardResponse(
    int totalArticles,
    int publishedArticles,
    long totalViews,
    int searches,
    int newsletterSubscribers,
    List<Article> mostViewed,
    List<Article> recentlyPublished,
    List<CategoryMetric> categoryMetrics,
    List<SearchEvent> recentSearches) {

  public record CategoryMetric(String id, String name, int articles, long views) {}
}
