package com.zhilu.delivery.document;

import java.util.List;

public interface OutlineClient {
  OutlineDocument create(
      String title, String text, String collectionId, String parentDocumentId, boolean publish);

  OutlineDocument info(String documentId);

  List<OutlineDocument> children(String parentDocumentId);

  OutlineDocument update(String documentId, String title, String text);

  String exportMarkdown(String documentId);

  boolean isConfigured();
}
