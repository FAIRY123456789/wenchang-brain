package cn.wenchang.mcp;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.stereotype.Component;

/** Professional, human-readable DOCX writer for public task artifacts. */
@Component
public class WordArtifactWriter {

    private static final Pattern NUMBERED = Pattern.compile("^\\d+[.、)]\\s*.*");
    private static final Pattern JSON_PROTOCOL = Pattern.compile("(?s)^\\s*[\\[{].*(\\\"tool\\\"|\\\"results\\\"|\\\"content\\\").*[\\]}]\\s*$");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'CST'");
    private static final String FONT = "Microsoft YaHei";
    private static final String ACCENT = "1F6F8B";

    public void write(Path path, String title, String topic, String content, List<String> sources) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            configureA4Page(document);
            titleBlock(document, safe(title, "文昌专题研究报告"), topic);
            appendContent(document, sanitizeContent(content));
            appendSources(document, sources);
            appendFooter(document);
            try (OutputStream output = Files.newOutputStream(path)) { document.write(output); }
        }
    }

    private void titleBlock(XWPFDocument document, String title, String topic) {
        XWPFParagraph titleParagraph = paragraph(document, title, 22, true, ACCENT,
                ParagraphAlignment.LEFT, 0, 100);
        titleParagraph.setKeepNext(true);
        if (topic != null && !topic.isBlank()) {
            paragraph(document, topic.trim(), 11, false, "58717A",
                    ParagraphAlignment.LEFT, 0, 80).setKeepNext(true);
        }
        paragraph(document, "文昌智脑 · 公开资料整理", 9, false, "7B8794",
                ParagraphAlignment.LEFT, 0, 240);
    }

    private void appendContent(XWPFDocument document, String content) {
        String[] lines = safe(content, "暂无可核验的正文内容。").replace("\r", "").split("\n", -1);
        for (int index = 0; index < lines.length;) {
            String line = lines[index].strip();
            if (isTableHeader(lines, index)) {
                List<List<String>> rows = new ArrayList<>();
                rows.add(tableCells(lines[index]));
                index += 2;
                while (index < lines.length && lines[index].strip().startsWith("|")) {
                    rows.add(tableCells(lines[index++]));
                }
                appendTable(document, rows);
                continue;
            }
            appendContentLine(document, line);
            index++;
        }
    }

    private void appendContentLine(XWPFDocument document, String line) {
        if (line.isBlank()) return;
        if (JSON_PROTOCOL.matcher(line).matches()) return;
        if (line.startsWith("### ")) heading(document, line.substring(4), 12, 160, 60);
        else if (line.startsWith("## ") || line.startsWith("# ")) {
            heading(document, line.replaceFirst("^#{1,2}\\s+", ""), 15, 240, 90);
        }
        else if (line.matches("^[-*+]\\s+.*")) listParagraph(document, line.replaceFirst("^[-*+]\\s+", ""), false);
        else if (NUMBERED.matcher(line).matches()) listParagraph(document, line, true);
        else paragraph(document, stripMarkdown(line), 10, false, "263746", ParagraphAlignment.LEFT, 0, 100);
    }

    private void appendTable(XWPFDocument document, List<List<String>> rows) {
        if (rows.isEmpty()) return;
        int columns = rows.get(0).size();
        XWPFTable table = document.createTable(rows.size(), columns);
        table.setWidth("100%");
        table.setCellMargins(100, 120, 100, 120);
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            XWPFTableRow row = table.getRow(rowIndex);
            for (int column = 0; column < columns; column++) {
                XWPFTableCell cell = row.getCell(column);
                cell.removeParagraph(0);
                XWPFParagraph paragraph = cell.addParagraph();
                paragraph.setSpacingAfter(0);
                addRun(paragraph, column < rows.get(rowIndex).size() ? rows.get(rowIndex).get(column) : "",
                        rowIndex == 0 ? 9 : 9, rowIndex == 0, rowIndex == 0 ? "FFFFFF" : "263746");
                if (rowIndex == 0) cell.setColor(ACCENT);
                else if (rowIndex % 2 == 0) cell.setColor("F4F8FA");
                CTTblWidth width = cell.getCTTc().isSetTcPr() && cell.getCTTc().getTcPr().isSetTcW()
                        ? cell.getCTTc().getTcPr().getTcW() : cell.getCTTc().addNewTcPr().addNewTcW();
                width.setType(STTblWidth.DXA);
                width.setW(BigInteger.valueOf(Math.max(1100, 9000L / columns)));
            }
        }
        document.createParagraph().setSpacingAfter(80);
    }

    private void appendSources(XWPFDocument document, List<String> sources) {
        heading(document, "来源与核验", 15, 240, 90);
        List<String> safeSources = sources == null ? List.of() : sources.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim).filter(value -> !value.contains("nominatim.openstreetmap.org/search?"))
                .distinct().toList();
        if (safeSources.isEmpty()) {
            paragraph(document, "本次未取得可直接引用的外部来源链接。", 9, false, "58717A",
                    ParagraphAlignment.LEFT, 0, 80);
        } else {
            for (int index = 0; index < safeSources.size(); index++) {
                listParagraph(document, (index + 1) + ". " + safeSources.get(index), true);
            }
        }
        paragraph(document, "说明：来源用于公开信息追溯；名单、政策效力、开放状态和招生安排应以主管单位最新公告为准。",
                9, false, "6B7280", ParagraphAlignment.LEFT, 80, 80);
    }

    private void appendFooter(XWPFDocument document) {
        XWPFParagraph footer = document.createParagraph();
        footer.setAlignment(ParagraphAlignment.RIGHT);
        footer.setSpacingBefore(160);
        addRun(footer, "生成时间：" + ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).format(TIME_FORMAT),
                8, false, "7B8794");
    }

    private void configureA4Page(XWPFDocument document) {
        CTSectPr section = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr() : document.getDocument().getBody().addNewSectPr();
        CTPageSz pageSize = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
        pageSize.setW(BigInteger.valueOf(11906)); pageSize.setH(BigInteger.valueOf(16838));
        CTPageMar margins = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        margins.setTop(BigInteger.valueOf(1134)); margins.setBottom(BigInteger.valueOf(1134));
        margins.setLeft(BigInteger.valueOf(1276)); margins.setRight(BigInteger.valueOf(1276));
        margins.setHeader(BigInteger.valueOf(567)); margins.setFooter(BigInteger.valueOf(567));
        margins.setGutter(BigInteger.ZERO);
    }

    private XWPFParagraph heading(XWPFDocument document, String text, int size, int before, int after) {
        XWPFParagraph paragraph = paragraph(document, stripMarkdown(text), size, true, ACCENT,
                ParagraphAlignment.LEFT, before, after);
        paragraph.setKeepNext(true);
        return paragraph;
    }

    private void listParagraph(XWPFDocument document, String text, boolean numbered) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setIndentationLeft(420); paragraph.setIndentationHanging(220);
        paragraph.setSpacingAfter(70); paragraph.setSpacingBetween(1.15);
        String value = numbered ? text : "• " + text;
        addRun(paragraph, stripMarkdown(value), 10, false, "263746");
    }

    private XWPFParagraph paragraph(XWPFDocument document, String text, int size, boolean bold, String color,
                                    ParagraphAlignment alignment, int before, int after) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(alignment); paragraph.setSpacingBefore(before); paragraph.setSpacingAfter(after);
        paragraph.setSpacingBetween(1.15);
        addRun(paragraph, text, size, bold, color);
        return paragraph;
    }

    private void addRun(XWPFParagraph paragraph, String text, int size, boolean bold, String color) {
        XWPFRun run = paragraph.createRun();
        run.setText(text == null ? "" : text); run.setFontFamily(FONT); run.setFontSize(size);
        run.setBold(bold); run.setColor(color);
        CTRPr properties = run.getCTR().isSetRPr() ? run.getCTR().getRPr() : run.getCTR().addNewRPr();
        CTFonts fonts = properties.sizeOfRFontsArray() > 0 ? properties.getRFontsArray(0) : properties.addNewRFonts();
        fonts.setAscii(FONT); fonts.setHAnsi(FONT); fonts.setEastAsia(FONT);
    }

    private boolean isTableHeader(String[] lines, int index) {
        if (index + 1 >= lines.length || !lines[index].strip().startsWith("|")) return false;
        return lines[index + 1].strip().matches("^\\|?[\\s:|-]+\\|?$");
    }

    private List<String> tableCells(String line) {
        String value = line.strip();
        if (value.startsWith("|")) value = value.substring(1);
        if (value.endsWith("|")) value = value.substring(0, value.length() - 1);
        List<String> cells = new ArrayList<>();
        for (String cell : value.split("\\|", -1)) cells.add(stripMarkdown(cell.trim()));
        return cells;
    }

    private String sanitizeContent(String value) {
        String text = safe(value, "暂无可核验的正文内容。");
        return text.replaceAll("(?s)```(?:json)?\\s*[\\[{].*?[\\]}]\\s*```", "")
                .replaceAll("(?m)^\\s*(searchPublicServices|searchStudyTourPlaces|searchTownshipProfile|knowledgeEvidence|webSearch|officialSourceSearch)\\s*$", "")
                .replaceAll("(?m)^\\s*\\[\\{\\\"text\\\":\\\".*$", "")
                .replaceAll("(?m)^以下是本次公开任务步骤取得的工具结果.*$", "")
                .trim();
    }

    private String stripMarkdown(String value) {
        return (value == null ? "" : value).replaceAll("\\*\\*(.*?)\\*\\*", "$1")
                .replaceAll("`([^`]*)`", "$1").trim();
    }

    private String safe(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
