package com.zhilu.delivery.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:catalog;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none"
})
@AutoConfigureMockMvc(addFilters = false)
class ProductCatalogIT {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void cleanCatalog() {
    jdbc.update("delete from product_version");
    jdbc.update("delete from product");
  }

  @Test
  void createsProductsAndKeepsVersionsScopedToTheirProduct() throws Exception {
    long firstProduct = createProduct("ERP", "智鹿 ERP");
    long secondProduct = createProduct("CRM", "智鹿 CRM");

    String versionJson = mvc.perform(post("/api/v1/products/{id}/versions", firstProduct)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"versionName\":\"V5.2\",\"releaseDate\":\"2026-07-01\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.productId").value(firstProduct))
        .andExpect(jsonPath("$.versionName").value("V5.2"))
        .andReturn().getResponse().getContentAsString();
    long versionId = new com.fasterxml.jackson.databind.ObjectMapper()
        .readTree(versionJson).get("id").asLong();

    mvc.perform(get("/api/v1/products/{productId}/versions/{versionId}",
            secondProduct, versionId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  private long createProduct(String code, String name) throws Exception {
    String json = mvc.perform(post("/api/v1/products")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"" + code + "\",\"name\":\"" + name
                + "\",\"category\":\"企业应用\"}"))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).get("id").asLong();
  }
}
