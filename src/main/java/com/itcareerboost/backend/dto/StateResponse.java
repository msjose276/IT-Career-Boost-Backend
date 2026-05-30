package com.itcareerboost.backend.dto;

import com.itcareerboost.backend.model.Article;
import com.itcareerboost.backend.model.Category;
import com.itcareerboost.backend.model.Tag;
import java.util.List;

public record StateResponse(List<Article> articles, List<Category> categories, List<Tag> tags) {}
