package com.zhilu.delivery.document;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class DocumentExportServiceTest {

  @Test
  void exportsOneMarkdownDocumentInFourSafeFormats() throws Exception {
    DocumentExportService service = new DocumentExportService(new MarkdownRenderer());
    DocumentView document = new DocumentView(
        1, "验收报告",
        "# 验收报告\n\n交付完成。\n\n- 功能通过\n- 数据通过\n\n"
            + "| 检查项 | 结果 |\n| --- | --- |\n| 登录 | 通过 |\n\n"
            + "```java\nSystem.out.println(\"ok\");\n```\n\n"
            + "<script>alert('unsafe')</script>",
        3, Instant.parse("2026-07-16T08:00:00Z"), "READY", null, null);

    DocumentExportService.Result markdown = service.export(document, "md");
    assertTrue(new String(markdown.getBytes(), UTF_8).contains("# 验收报告"));
    assertEquals("text/markdown;charset=UTF-8", markdown.getContentType());

    DocumentExportService.Result html = service.export(document, "html");
    String htmlText = new String(html.getBytes(), UTF_8);
    assertTrue(htmlText.contains("<table>"));
    assertTrue(htmlText.contains("<pre><code"));
    assertFalse(htmlText.contains("<script>"));

    DocumentExportService.Result pdf = service.export(document, "pdf");
    assertEquals("%PDF-", new String(pdf.getBytes(), 0, 5, ISO_8859_1));

    DocumentExportService.Result word = service.export(document, "docx");
    try (XWPFDocument docx = new XWPFDocument(new ByteArrayInputStream(word.getBytes()))) {
      assertTrue(docx.getParagraphs().stream()
          .anyMatch(paragraph -> paragraph.getText().contains("验收报告")));
      assertEquals(1, docx.getTables().size());
    }
  }

  @Test
  void rejectsUnknownExportFormat() {
    DocumentExportService service = new DocumentExportService(new MarkdownRenderer());
    DocumentView document = new DocumentView(
        1, "文档", "正文", 1, Instant.now(), "READY", null, null);

    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class, () -> service.export(document, "zip"));
  }
}
