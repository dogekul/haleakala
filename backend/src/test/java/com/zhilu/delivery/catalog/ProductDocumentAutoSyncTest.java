package com.zhilu.delivery.catalog;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.zhilu.delivery.audit.AuditService;
import org.junit.jupiter.api.Test;

class ProductDocumentAutoSyncTest {
  @Test
  void productCatalogDoesNotDependOnDocumentSynchronization() {
    assertDoesNotThrow(() -> ProductCatalogController.class.getConstructor(
        ProductCatalogService.class, AuditService.class));
  }

  @Test
  void productStructureDoesNotDependOnDocumentSynchronization() {
    assertDoesNotThrow(() -> ProductStructureController.class.getConstructor(
        ProductStructureService.class, ProductVersionFeatureService.class));
  }
}
