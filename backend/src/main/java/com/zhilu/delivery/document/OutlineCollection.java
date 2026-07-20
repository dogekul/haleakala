package com.zhilu.delivery.document;

public final class OutlineCollection {
  private final String id;
  private final String name;
  private final String urlId;

  public OutlineCollection(String id, String name, String urlId) {
    this.id = id;
    this.name = name;
    this.urlId = urlId;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getUrlId() {
    return urlId;
  }
}
