package com.zhilu.delivery.document;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Image;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.AttributeProvider;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

@Component
public class MarkdownRenderer {
  private final Parser parser;
  private final HtmlRenderer html;

  public MarkdownRenderer() {
    List<Extension> extensions = Arrays.<Extension>asList(TablesExtension.create());
    parser = Parser.builder().extensions(extensions).build();
    html = HtmlRenderer.builder()
        .extensions(extensions)
        .escapeHtml(true)
        .sanitizeUrls(true)
        .attributeProviderFactory(context -> new AttributeProvider() {
          @Override
          public void setAttributes(
              Node node, String tagName, Map<String, String> attributes) {
            if (node instanceof Image
                && !safeImage(((Image) node).getDestination())) {
              attributes.remove("src");
            }
          }
        })
        .build();
  }

  public Node parse(String markdown) {
    return parser.parse(markdown == null ? "" : markdown);
  }

  public String renderPage(String title, String markdown) {
    String body = renderFragment(markdown);
    return "<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"zh-CN\">"
        + "<head><meta charset=\"UTF-8\" />"
        + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\" />"
        + "<title>" + escape(title) + "</title><style type=\"text/css\">"
        + "@page{size:A4;margin:18mm 16mm}"
        + "body{max-width:920px;margin:0 auto;padding:28px 34px;color:#1f2329;"
        + "font-family:'Noto Sans CJK SC','Microsoft YaHei','PingFang SC',Arial,sans-serif;"
        + "font-size:15px;line-height:1.75}"
        + "h1,h2,h3,h4{color:#1f2329;line-height:1.35;margin:1.4em 0 .55em}"
        + "h1{font-size:30px;border-bottom:1px solid #e5e6eb;padding-bottom:12px}"
        + "h2{font-size:23px}h3{font-size:19px}"
        + "a{color:#3370ff;text-decoration:none}blockquote{margin:16px 0;padding:8px 16px;"
        + "border-left:4px solid #8f959e;background:#f5f6f7;color:#646a73}"
        + "table{border-collapse:collapse;width:100%;margin:18px 0}"
        + "th,td{border:1px solid #dee0e3;padding:9px 12px;text-align:left}"
        + "th{background:#f5f6f7;font-weight:600}"
        + "pre{overflow:auto;background:#1f2329;color:#f5f6f7;border-radius:8px;"
        + "padding:16px;font:13px/1.6 Menlo,Consolas,monospace}"
        + "code{font-family:Menlo,Consolas,monospace;background:#f2f3f5;"
        + "border-radius:4px;padding:2px 5px}pre code{background:none;padding:0}"
        + "img{max-width:100%;height:auto}"
        + "</style></head><body>" + body + "</body></html>";
  }

  public String renderFragment(String markdown) {
    return html.render(parse(markdown));
  }

  private String escape(String value) {
    if (value == null) return "";
    return value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  private boolean safeImage(String destination) {
    if (destination == null) return false;
    String value = destination.trim();
    return !value.isEmpty() && !value.startsWith("//") && value.indexOf(':') < 0;
  }
}
