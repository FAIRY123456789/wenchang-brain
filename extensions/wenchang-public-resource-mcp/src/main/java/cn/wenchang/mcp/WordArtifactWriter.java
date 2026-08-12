package cn.wenchang.mcp;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.math.BigInteger;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.stereotype.Component;

@Component
public class WordArtifactWriter {

    private static final Pattern NUMBERED = Pattern.compile("^\\d+[.、．)]\\s*.*");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    public void write(Path path, String title, String topic, String content, List<String> sources) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            configureA4Page(document);
            paragraph(document, safe(title, "文昌专题报告"), "Title", 22, true, ParagraphAlignment.CENTER);
            if (topic != null && !topic.isBlank()) {
                paragraph(document, topic.trim(), "Subtitle", 12, false, ParagraphAlignment.CENTER);
            }
            for (String rawLine : safe(content, "暂无正文内容。").replace("\r", "").split("\n", -1)) {
                appendContentLine(document, rawLine);
            }
            paragraph(document, "来源", "Heading1", 16, true, ParagraphAlignment.LEFT);
            List<String> safeSources = sources == null ? List.of() : sources.stream()
                    .filter(value -> value != null && !value.isBlank()).distinct().toList();
            if (safeSources.isEmpty()) {
                paragraph(document, "未提供外部来源。", null, 10, false, ParagraphAlignment.LEFT);
            }
            else {
                for (String source : safeSources) listParagraph(document, source.trim(), false);
            }
            paragraph(document, "生成时间：" + ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).format(TIME_FORMAT),
                    null, 9, false, ParagraphAlignment.LEFT);
            try (OutputStream output = Files.newOutputStream(path)) { document.write(output); }
        }
    }

    private void configureA4Page(XWPFDocument document) {
        CTSectPr section = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr()
                : document.getDocument().getBody().addNewSectPr();
        CTPageSz pageSize = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
        pageSize.setW(BigInteger.valueOf(11906));
        pageSize.setH(BigInteger.valueOf(16838));
        CTPageMar margins = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        BigInteger standardMargin = BigInteger.valueOf(1440);
        margins.setTop(standardMargin);
        margins.setBottom(standardMargin);
        margins.setLeft(standardMargin);
        margins.setRight(standardMargin);
        margins.setHeader(BigInteger.valueOf(720));
        margins.setFooter(BigInteger.valueOf(720));
        margins.setGutter(BigInteger.ZERO);
    }

    private void appendContentLine(XWPFDocument document, String rawLine) {
        String line = rawLine == null ? "" : rawLine.stripTrailing();
        if (line.isBlank()) {
            document.createParagraph();
        }
        else if (line.startsWith("### ")) {
            paragraph(document, line.substring(4), "Heading2", 13, true, ParagraphAlignment.LEFT);
        }
        else if (line.startsWith("## ")) {
            paragraph(document, line.substring(3), "Heading1", 16, true, ParagraphAlignment.LEFT);
        }
        else if (line.startsWith("# ")) {
            paragraph(document, line.substring(2), "Heading1", 16, true, ParagraphAlignment.LEFT);
        }
        else if (line.matches("^[-*+]\\s+.*")) {
            listParagraph(document, line.replaceFirst("^[-*+]\\s+", ""), false);
        }
        else if (NUMBERED.matcher(line).matches()) {
            listParagraph(document, line, true);
        }
        else {
            paragraph(document, line, null, 11, false, ParagraphAlignment.LEFT);
        }
    }

    private void listParagraph(XWPFDocument document, String text, boolean numbered) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setIndentationLeft(360);
        paragraph.setIndentationHanging(180);
        addRun(paragraph, numbered ? text : "• " + text, 11, false);
    }

    private void paragraph(XWPFDocument document, String text, String style, int size,
                           boolean bold, ParagraphAlignment alignment) {
        XWPFParagraph paragraph = document.createParagraph();
        if (style != null) paragraph.setStyle(style);
        paragraph.setAlignment(alignment);
        paragraph.setSpacingAfter(120);
        addRun(paragraph, text, size, bold);
    }

    private void addRun(XWPFParagraph paragraph, String text, int size, boolean bold) {
        XWPFRun run = paragraph.createRun();
        run.setText(text == null ? "" : text);
        run.setFontFamily("Microsoft YaHei");
        run.setFontSize(size);
        run.setBold(bold);
        CTRPr properties = run.getCTR().isSetRPr() ? run.getCTR().getRPr() : run.getCTR().addNewRPr();
        CTFonts fonts = properties.sizeOfRFontsArray() > 0
                ? properties.getRFontsArray(0) : properties.addNewRFonts();
        fonts.setEastAsia("Microsoft YaHei");
    }

    private String safe(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
