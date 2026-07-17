package com.zhilu.delivery.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhilu.delivery.audit.AuditService;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductDocumentAutoSyncTest {
  private final CurrentUser user = new CurrentUser(
      9L, 7L, "product", "产品经理", Collections.<String>emptyList(),
      Collections.<String>emptyList());

  @Test
  void productCreationSurvivesOutlineInitializationFailure() {
    ProductCatalogService catalog = mock(ProductCatalogService.class);
    ProductDocumentService documents = mock(ProductDocumentService.class);
    AuditService audit = mock(AuditService.class);
    Map<String, Object> created = value(21L);
    when(catalog.createProduct(anyLong(), any(), any(), any(), any(), any()))
        .thenReturn(created);
    doThrow(new IllegalStateException("Outline unavailable"))
        .when(documents).syncProduct(7L, 21L);
    ProductCatalogController controller =
        new ProductCatalogController(catalog, audit, documents);

    ProductCatalogController.ProductRequest request =
        new ProductCatalogController.ProductRequest();
    request.code = "ERP";
    request.name = "ERP";
    assertEquals(created, controller.create(request, user));
    verify(documents).syncProduct(7L, 21L);
  }

  @Test
  void featureCreationTriggersSpecInitializationButKeepsSavedFeatureOnFailure() {
    ProductStructureService structures = mock(ProductStructureService.class);
    ProductVersionFeatureService manifests = mock(ProductVersionFeatureService.class);
    ProductDocumentService documents = mock(ProductDocumentService.class);
    Map<String, Object> created = value(31L);
    when(structures.saveFeature(anyLong(), anyLong(), anyLong(), any(), anyLong(), any(),
        any(), any(), any(), any(), anyLong())).thenReturn(created);
    doThrow(new IllegalStateException("template missing"))
        .when(documents).syncFeature(7L, 21L, 31L);
    ProductStructureController controller =
        new ProductStructureController(structures, manifests, documents);

    ProductStructureController.FeatureRequest request =
        new ProductStructureController.FeatureRequest();
    request.moduleId = 22L;
    request.code = "REPORT";
    request.name = "报表";
    assertEquals(created, controller.createFeature(21L, request, user));
    verify(documents).syncFeature(7L, 21L, 31L);
  }

  private Map<String, Object> value(long id) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", Long.valueOf(id));
    return result;
  }
}
