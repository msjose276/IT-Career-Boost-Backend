package com.itcareerboost.backend.controller;

import com.itcareerboost.backend.dto.DashboardResponse;
import com.itcareerboost.backend.dto.StateResponse;
import com.itcareerboost.backend.model.Article;
import com.itcareerboost.backend.model.Category;
import com.itcareerboost.backend.model.Tag;
import com.itcareerboost.backend.service.AuthService;
import com.itcareerboost.backend.service.ContentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ContentController {
  private final AuthService authService;
  private final ContentService contentService;

  public ContentController(AuthService authService, ContentService contentService) {
    this.authService = authService;
    this.contentService = contentService;
  }

  @GetMapping("/state")
  public StateResponse publicState() {
    return contentService.publicState();
  }

  @GetMapping("/articles")
  public List<Article> articles(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String tag,
      @RequestParam(defaultValue = "newest") String sort) {
    return contentService.findArticles(q, category, tag, sort);
  }

  @GetMapping("/articles/{slug}")
  public Article article(@PathVariable String slug) {
    return contentService.findPublishedBySlug(slug);
  }

  @GetMapping("/admin/state")
  public StateResponse adminState(@RequestHeader(value = "Authorization", required = false) String authorization) {
    authService.requireAdmin(authorization);
    return contentService.adminState();
  }

  @GetMapping("/admin/dashboard")
  public DashboardResponse dashboard(@RequestHeader(value = "Authorization", required = false) String authorization) {
    authService.requireAdmin(authorization);
    return contentService.dashboard();
  }

  @PostMapping("/admin/articles")
  @ResponseStatus(HttpStatus.CREATED)
  public Article createArticle(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @Valid @RequestBody Article article) {
    authService.requireAdmin(authorization);
    return contentService.saveArticle(article);
  }

  @PutMapping("/admin/articles/{id}")
  public Article updateArticle(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @PathVariable String id,
      @Valid @RequestBody Article article) {
    authService.requireAdmin(authorization);
    article.setId(id);
    return contentService.saveArticle(article);
  }

  @DeleteMapping("/admin/articles/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteArticle(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @PathVariable String id) {
    authService.requireAdmin(authorization);
    contentService.deleteArticle(id);
  }

  @PostMapping("/admin/categories")
  @ResponseStatus(HttpStatus.CREATED)
  public Category createCategory(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @Valid @RequestBody Category category) {
    authService.requireAdmin(authorization);
    return contentService.addCategory(category);
  }

  @PostMapping("/admin/tags")
  @ResponseStatus(HttpStatus.CREATED)
  public Tag createTag(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @Valid @RequestBody Tag tag) {
    authService.requireAdmin(authorization);
    return contentService.addTag(tag);
  }
}
