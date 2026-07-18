package com.clicker.mousehub;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test void publicCatalogAndOptionsAreAvailable() throws Exception {
        mvc.perform(get("/api/v1/mice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.page.number", is(1)));
        mvc.perform(get("/api/v1/review-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gripStyles", hasSize(4)))
                .andExpect(jsonPath("$.proTags", hasSize(9)));
    }

    @Test void registrationReturnsJwtAndMeRequiresToken() throws Exception {
        String body = "{\"email\":\"USER@example.com\",\"password\":\"password123\"}";
        String response = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyString())))
                .andExpect(jsonPath("$.user.email", is("user@example.com")))
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(response).get("token").asText();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role", is("USER")));
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }
}
