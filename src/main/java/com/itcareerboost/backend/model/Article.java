package com.itcareerboost.backend.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Article {
  private String id = UUID.randomUUID().toString();

  @NotBlank private String title;
  @NotBlank private String slug;
  @NotBlank private String summary;
  @NotBlank private String content;
  @NotBlank private String category;
  private List<String> tags = new ArrayList<>();
  @NotBlank private String author;
  @NotNull private ArticleStatus status = ArticleStatus.draft;
  private LocalDate publishedAt;
  private LocalDate scheduledAt;
  private long views;
  private boolean featured;
  private boolean editorPick;
  @NotBlank private String image;
  private String seoTitle;
  private String seoDescription;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getSlug() {
    return slug;
  }

  public void setSlug(String slug) {
    this.slug = slug;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public List<String> getTags() {
    return tags;
  }

  public void setTags(List<String> tags) {
    this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
  }

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = author;
  }

  public ArticleStatus getStatus() {
    return status;
  }

  public void setStatus(ArticleStatus status) {
    this.status = status;
  }

  public LocalDate getPublishedAt() {
    return publishedAt;
  }

  public void setPublishedAt(LocalDate publishedAt) {
    this.publishedAt = publishedAt;
  }

  public LocalDate getScheduledAt() {
    return scheduledAt;
  }

  public void setScheduledAt(LocalDate scheduledAt) {
    this.scheduledAt = scheduledAt;
  }

  public long getViews() {
    return views;
  }

  public void setViews(long views) {
    this.views = views;
  }

  public boolean isFeatured() {
    return featured;
  }

  public void setFeatured(boolean featured) {
    this.featured = featured;
  }

  public boolean isEditorPick() {
    return editorPick;
  }

  public void setEditorPick(boolean editorPick) {
    this.editorPick = editorPick;
  }

  public String getImage() {
    return image;
  }

  public void setImage(String image) {
    this.image = image;
  }

  public String getSeoTitle() {
    return seoTitle;
  }

  public void setSeoTitle(String seoTitle) {
    this.seoTitle = seoTitle;
  }

  public String getSeoDescription() {
    return seoDescription;
  }

  public void setSeoDescription(String seoDescription) {
    this.seoDescription = seoDescription;
  }
}
