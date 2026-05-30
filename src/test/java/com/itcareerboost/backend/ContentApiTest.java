package com.itcareerboost.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ContentApiTest {
  @Autowired private MockMvc mockMvc;

  @Test
  void publicStateReturnsPublishedContentAndTaxonomy() throws Exception {
    mockMvc.perform(get("/api/state"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.articles", hasSize(84)))
        .andExpect(jsonPath("$.categories", hasSize(12)))
        .andExpect(jsonPath("$.tags", hasSize(14)));
  }

  @Test
  void seededArticlesIncludePracticalLongFormContent() throws Exception {
    String response = mockMvc.perform(get("/api/articles/how-to-get-promoted-as-a-software-engineer"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", containsString("What good looks like")))
        .andExpect(jsonPath("$.content", containsString("A practical way to start")))
        .andExpect(jsonPath("$.content", containsString("Mistakes to avoid")))
        .andExpect(jsonPath("$.content", containsString("30-day action plan")))
        .andReturn()
        .getResponse()
        .getContentAsString();

    assertThat(response).contains("The professionals who grow fastest");
    assertThat(response.length()).isGreaterThan(5000);
  }

  @Test
  void adminDashboardRequiresToken() throws Exception {
    mockMvc.perform(get("/api/admin/dashboard"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void adminCanLoginAndReadDashboard() throws Exception {
    String response = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"admin@itcareerboost.local\",\"password\":\"careerboost\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isString())
        .andReturn()
        .getResponse()
        .getContentAsString();

    String token = response.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

    mockMvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalArticles").value(84))
        .andExpect(jsonPath("$.publishedArticles").value(84))
        .andExpect(jsonPath("$.categoryMetrics", hasSize(12)))
        .andExpect(jsonPath("$.categoryMetrics[0].articles").value(7))
        .andExpect(jsonPath("$.totalViews", greaterThan(100000)));
  }
}
