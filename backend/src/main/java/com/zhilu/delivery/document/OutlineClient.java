package com.zhilu.delivery.document;

import java.util.List;

public interface OutlineClient {
  OutlineDocument create(
      OutlineConnection connection, String documentId, String title, String text,
      String collectionId,
      String parentDocumentId, boolean publish);

  OutlineDocument info(OutlineConnection connection, String documentId);

  List<OutlineDocument> children(
      OutlineConnection connection, String parentDocumentId);

  OutlineDocument update(
      OutlineConnection connection, String documentId, String title, String text);

  OutlineCollection collectionInfo(
      OutlineConnection connection, String collectionReference);

  OutlineCollection testConnection(
      OutlineConnection connection, String collectionReference);

  String exportMarkdown(OutlineConnection connection, String documentId);
}
