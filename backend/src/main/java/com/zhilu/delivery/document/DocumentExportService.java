package com.zhilu.delivery.document;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.springframework.stereotype.Service;

@Service
public class DocumentExportService {
  private static final String DOCX_TYPE =
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
  private final MarkdownRenderer renderer;

  public DocumentExportService(MarkdownRenderer renderer) {
    this.renderer = renderer;
  }

  public Result export(DocumentView document, String requestedFormat) {
    String format = requestedFormat == null
        ? "md" : requestedFormat.trim().toLowerCase(Locale.ROOT);
    if ("md".equals(format) || "markdown".equals(format)) {
      return new Result(bytes(document.getMarkdown()), "text/markdown;charset=UTF-8", "md");
    }
    String html = renderer.renderPage(document.getTitle(), document.getMarkdown());
    if ("html".equals(format)) {
      return new Result(bytes(html), "text/html;charset=UTF-8", "html");
    }
    if ("pdf".equals(format)) {
      return new Result(pdf(html), "application/pdf", "pdf");
    }
    if ("docx".equals(format) || "word".equals(format)) {
      return new Result(word(renderer.parse(document.getMarkdown())), DOCX_TYPE, "docx");
    }
    throw new IllegalArgumentException("导出格式仅支持 md、html、pdf 或 docx");
  }

  private byte[] pdf(String html) {
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      PdfRendererBuilder builder = new PdfRendererBuilder();
      builder.useFastMode();
      builder.withHtmlContent(html, null);
      builder.toStream(output);
      builder.run();
      return output.toByteArray();
    } catch (Exception failure) {
      throw new IllegalStateException("PDF 导出失败", failure);
    }
  }

  private byte[] word(Node root) {
    try (XWPFDocument document = new XWPFDocument();
         ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      for (Node child = root.getFirstChild(); child != null; child = child.getNext()) {
        appendBlock(document, child);
      }
      document.write(output);
      return output.toByteArray();
    } catch (IOException failure) {
      throw new IllegalStateException("Word 导出失败", failure);
    }
  }

  private void appendBlock(XWPFDocument document, Node node) {
    if (node instanceof Heading) {
      XWPFParagraph paragraph = document.createParagraph();
      paragraph.setStyle("Heading" + ((Heading) node).getLevel());
      appendInline(paragraph, node, false, false);
      return;
    }
    if (node instanceof Paragraph) {
      XWPFParagraph paragraph = document.createParagraph();
      appendInline(paragraph, node, false, false);
      return;
    }
    if (node instanceof FencedCodeBlock || node instanceof IndentedCodeBlock) {
      XWPFParagraph paragraph = document.createParagraph();
      XWPFRun run = paragraph.createRun();
      run.setFontFamily("Consolas");
      run.setFontSize(10);
      run.setText(node instanceof FencedCodeBlock
          ? ((FencedCodeBlock) node).getLiteral()
          : ((IndentedCodeBlock) node).getLiteral());
      return;
    }
    if (node instanceof TableBlock) {
      appendTable(document, node);
      return;
    }
    if (node instanceof BulletList || node instanceof OrderedList) {
      appendList(document, node, node instanceof OrderedList);
      return;
    }
    if (node instanceof BlockQuote) {
      for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun prefix = paragraph.createRun();
        prefix.setColor("646A73");
        prefix.setText("│ ");
        appendInline(paragraph, child, false, false);
      }
      return;
    }
    for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
      appendBlock(document, child);
    }
  }

  private void appendList(XWPFDocument document, Node list, boolean ordered) {
    int number = list instanceof OrderedList ? ((OrderedList) list).getStartNumber() : 1;
    for (Node item = list.getFirstChild(); item != null; item = item.getNext()) {
      if (!(item instanceof ListItem)) continue;
      XWPFParagraph paragraph = document.createParagraph();
      paragraph.setIndentationLeft(360);
      paragraph.createRun().setText(ordered ? number++ + ". " : "• ");
      Node content = item.getFirstChild();
      if (content != null) appendInline(paragraph, content, false, false);
      for (Node nested = content == null ? null : content.getNext();
           nested != null; nested = nested.getNext()) {
        appendBlock(document, nested);
      }
    }
  }

  private void appendTable(XWPFDocument document, Node tableBlock) {
    List<List<String>> rows = new ArrayList<List<String>>();
    collectTableRows(tableBlock, rows);
    if (rows.isEmpty()) return;
    int columns = 1;
    for (List<String> row : rows) columns = Math.max(columns, row.size());
    XWPFTable table = document.createTable(rows.size(), columns);
    for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
      XWPFTableRow targetRow = table.getRow(rowIndex);
      List<String> sourceRow = rows.get(rowIndex);
      for (int column = 0; column < sourceRow.size(); column++) {
        XWPFTableCell cell = targetRow.getCell(column);
        cell.setText(sourceRow.get(column));
        if (rowIndex == 0 && !cell.getParagraphs().isEmpty()) {
          for (XWPFRun run : cell.getParagraphs().get(0).getRuns()) run.setBold(true);
        }
      }
    }
  }

  private void collectTableRows(Node node, List<List<String>> rows) {
    if (node instanceof TableRow) {
      List<String> row = new ArrayList<String>();
      for (Node cell = node.getFirstChild(); cell != null; cell = cell.getNext()) {
        if (cell instanceof TableCell) row.add(text(cell));
      }
      rows.add(row);
      return;
    }
    for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
      collectTableRows(child, rows);
    }
  }

  private void appendInline(
      XWPFParagraph paragraph, Node parent, boolean bold, boolean italic) {
    for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
      if (node instanceof Text) {
        run(paragraph, ((Text) node).getLiteral(), bold, italic, false);
      } else if (node instanceof Code) {
        run(paragraph, ((Code) node).getLiteral(), bold, italic, true);
      } else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
        paragraph.createRun().addBreak();
      } else if (node instanceof StrongEmphasis) {
        appendInline(paragraph, node, true, italic);
      } else if (node instanceof Emphasis) {
        appendInline(paragraph, node, bold, true);
      } else if (node instanceof Link) {
        appendInline(paragraph, node, bold, italic);
        run(paragraph, " (" + ((Link) node).getDestination() + ")", false, false, false);
      } else if (node instanceof Image) {
        String label = text(node);
        run(paragraph,
            (label.isEmpty() ? "图片" : label) + " (" + ((Image) node).getDestination() + ")",
            bold, italic, false);
      } else if (node instanceof Paragraph) {
        appendInline(paragraph, node, bold, italic);
      } else {
        appendInline(paragraph, node, bold, italic);
      }
    }
  }

  private void run(
      XWPFParagraph paragraph, String value, boolean bold, boolean italic, boolean code) {
    XWPFRun run = paragraph.createRun();
    run.setBold(bold);
    run.setItalic(italic);
    if (code) run.setFontFamily("Consolas");
    String[] lines = (value == null ? "" : value).split("\\n", -1);
    for (int i = 0; i < lines.length; i++) {
      if (i > 0) run.addBreak();
      run.setText(lines[i]);
    }
  }

  private String text(Node node) {
    StringBuilder value = new StringBuilder();
    collectText(node, value);
    return value.toString();
  }

  private void collectText(Node node, StringBuilder value) {
    if (node instanceof Text) value.append(((Text) node).getLiteral());
    else if (node instanceof Code) value.append(((Code) node).getLiteral());
    for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
      collectText(child, value);
    }
  }

  private byte[] bytes(String value) {
    return (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
  }

  public static final class Result {
    private final byte[] bytes;
    private final String contentType;
    private final String extension;

    private Result(byte[] bytes, String contentType, String extension) {
      this.bytes = bytes;
      this.contentType = contentType;
      this.extension = extension;
    }

    public byte[] getBytes() {
      return bytes;
    }

    public String getContentType() {
      return contentType;
    }

    public String getExtension() {
      return extension;
    }
  }
}
